import concurrent.futures
import importlib.util
import json
import pathlib
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

spec=importlib.util.spec_from_file_location('baro',pathlib.Path(__file__).parents[1]/'baro.py')
baro=importlib.util.module_from_spec(spec);spec.loader.exec_module(baro)

class BackendTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.store=baro.Store(self.tmp.name+'/cache.sqlite');self.calls=[]
    def tearDown(self):self.tmp.cleanup()
    def weather_fetch(self,url,**kwargs):
        self.calls.append(url)
        if 'air-quality' in url:return {'timezone':'Australia/Brisbane','current':{'time':100,'us_aqi':48}}
        return {'timezone':'Australia/Brisbane','current':{'time':100},'hourly':{'time':[100]}}
    def test_real_http_health_validation_and_forecast(self):
        service=baro.Service(self.store,self.weather_fetch)
        server=ThreadingHTTPServer(('127.0.0.1',0),baro.handler_for(service));thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        try:
            base=f'http://127.0.0.1:{server.server_port}'
            health=json.load(urllib.request.urlopen(base+'/healthz'));self.assertEqual(health['service'],'baro');self.assertFalse(health['feedbackConfigured'])
            weather=json.load(urllib.request.urlopen(base+'/v1/weather?lat=-27.47&lon=153.03'))
            self.assertEqual(weather['latitude'],-27.47);self.assertIsNotNone(weather['air']);self.assertEqual(len(self.calls),2)
            for query in ('lat=NaN&lon=2','lat=91&lon=0','lat=0&lat=1&lon=0','lat=0&lon=0&url=x'):
                with self.assertRaises(urllib.error.HTTPError) as ctx:urllib.request.urlopen(base+'/v1/weather?'+query)
                self.assertEqual(ctx.exception.code,400)
            with self.assertRaises(urllib.error.HTTPError):urllib.request.urlopen(base+'/not-found')
        finally:server.shutdown();server.server_close();thread.join()
    def test_shared_cache_collapses_concurrent_requests(self):
        count=0;lock=threading.Lock()
        def load():
            nonlocal count
            with lock:count+=1
            time.sleep(.1);return {'v':12}
        def request(_):return baro.Store(self.store.path).cached('same',load)[0]
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:results=list(pool.map(request,range(8)))
        self.assertEqual(count,1);self.assertTrue(all(x=={'v':12} for x in results))
    def test_stale_if_error_has_original_timestamp(self):
        old=time.time()-4000
        with self.store.db() as db:db.execute('INSERT INTO cache VALUES(?,?,?)',('x',old,'{"v":1}'))
        def fail():raise baro.Failure(502,'down')
        value,at,stale=self.store.cached('x',fail)
        self.assertTrue(stale);self.assertEqual(at,old);self.assertEqual(value,{'v':1})
    def test_expired_cache_is_not_promoted(self):
        with self.store.db() as db:db.execute('INSERT INTO cache VALUES(?,?,?)',('x',time.time()-90000,'{}'))
        with self.assertRaises(baro.Failure):self.store.cached('x',lambda:(_ for _ in ()).throw(baro.Failure(502,'down')))
    def test_air_failure_keeps_weather_and_null_air(self):
        def fetch(url,**kwargs):
            if 'air-quality' in url:raise baro.Failure(502,'air_down')
            return self.weather_fetch(url)
        r=baro.Service(self.store,fetch).weather({'lat':['0'],'lon':['0']})
        self.assertIsNotNone(r['forecast']);self.assertIsNone(r['air']);self.assertEqual(r['airError'],'air_down')
    def test_budget_survives_new_store(self):
        self.store.reserve(500,time.time())
        with self.assertRaises(baro.Failure) as ctx:baro.Store(self.store.path).reserve(1,time.time())
        self.assertEqual(ctx.exception.code,'provider_budget')
    def test_malformed_forecast_not_cached(self):
        with self.assertRaises(baro.Failure):baro.Service(self.store,lambda *a,**k:{}).weather({'lat':['0'],'lon':['0']})
        with self.store.db() as db:self.assertEqual(db.execute('SELECT COUNT(*) FROM cache').fetchone()[0],0)
    def test_place_search_validation_and_cache(self):
        def fetch(url,**kwargs):self.calls.append(url);return {'results':[{'name':'Paris','latitude':48.86,'longitude':2.35,'timezone':'Europe/Paris','secret':'no'}]}
        s=baro.Service(self.store,fetch);r=s.search({'q':['Paris'],'lang':['en']});s.search({'q':['Paris'],'lang':['en']})
        self.assertEqual(len(self.calls),1);self.assertNotIn('secret',r['results'][0])
        for p in ({'q':['x'],'lang':['en']},{'q':['Paris'],'lang':['zz']},{'q':['Paris'],'lang':['en'],'url':['a']}):
            with self.assertRaises(baro.Failure):s.search(p)
    def feedback_service(self,mode='ok'):
        issues=[];posts=[]
        def fetch(url,body=None,**kwargs):
            if body is not None:
                posts.append(body);issue=dict(number=7,html_url='https://github.com/dpan538/baro/issues/7',body=body['body']);issues.append(issue)
                if mode=='timeout':raise baro.Failure(502,'timeout')
                if mode=='bad':return {'number':-1}
                return issue
            if '?state=' in url:return issues
            return issues[-1]
        return baro.Service(self.store,fetch,token='test-only'),posts
    def message(self):return dict(body='Testing an offline draft',requestId='baro-test-request-0001',version='0.10.0-native-preview')
    def test_feedback_receipt_is_retrieved_and_deduplicated(self):
        s,posts=self.feedback_service();r=s.feedback(self.message());again=s.feedback(self.message())
        self.assertTrue(r['accepted']);self.assertEqual(r,again);self.assertEqual(len(posts),1)
    def test_feedback_payload_rejects_unknown_and_conflicting_fields(self):
        s,_=self.feedback_service();s.feedback(self.message())
        with self.assertRaises(baro.Failure) as ctx:s.feedback(dict(self.message(),body='different'))
        self.assertEqual(ctx.exception.status,409)
        with self.assertRaises(baro.Failure):s.feedback(dict(self.message(),email='private'))
    def test_feedback_timeout_reconciles_before_second_post(self):
        s,posts=self.feedback_service('timeout')
        with self.assertRaises(baro.Failure):s.feedback(self.message())
        with self.store.db() as db:db.execute('UPDATE feedback SET at=?',(time.time()-30,))
        r=s.feedback(self.message());self.assertTrue(r['accepted']);self.assertEqual(len(posts),1)
    def test_feedback_invalid_receipt_never_accepts(self):
        s,_=self.feedback_service('bad')
        with self.assertRaises(baro.Failure):s.feedback(self.message())
        with self.store.db() as db:self.assertIsNone(db.execute('SELECT receipt FROM feedback').fetchone()[0])
    def test_feedback_missing_configuration(self):
        with self.assertRaises(baro.Failure) as ctx:baro.Service(self.store).feedback(self.message())
        self.assertEqual(ctx.exception.status,503)
    def test_feedback_rate_limit(self):
        s,_=self.feedback_service()
        with self.store.db() as db:
            for i in range(20):db.execute('INSERT INTO feedback VALUES(?,?,?,?,?)',(str(i),'x',time.time(),None,'pending'))
        with self.assertRaises(baro.Failure) as ctx:s.feedback(self.message())
        self.assertEqual(ctx.exception.status,429)

if __name__=='__main__':unittest.main()
