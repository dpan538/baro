import io
import json
import hashlib
import threading
import unittest
from datetime import datetime, timezone
from urllib.parse import urlparse, parse_qs
from wsgiref.simple_server import make_server, WSGIRequestHandler
import urllib.request
from github_relay import FeedbackApp, GitHubRelay, RelayError, API


class GitHub:
    def __init__(self):
        self.issues=[];self.posts=0;self.fail_after_write=False;self.invalid_receipt=False;self.failed_verification=False

    def __call__(self, method, url, data=None):
        if method=='GET':
            if url.startswith(API+'/'):
                issue=next((x for x in self.issues if x['html_url'].endswith('/'+url.rsplit('/',1)[1])),{})
                return 200, {**issue,'body':'unverified'} if self.failed_verification else issue
            page=int(parse_qs(urlparse(url).query).get('page',['1'])[0])
            return 200,self.issues[(page-1)*100:page*100]
        assert method=='POST' and url==API
        self.posts+=1
        issue={'html_url':'https://github.com/dpan538/baro/issues/'+str(self.posts),'body':data['body'],
               'created_at':datetime.now(timezone.utc).isoformat()}
        self.issues.insert(0,issue)
        if self.fail_after_write:
            self.fail_after_write=False;raise RelayError(503,'github_unavailable')
        if self.invalid_receipt:return 201,{**issue,'html_url':'https://other.invalid/2'}
        return 201,issue


class Tests(unittest.TestCase):
    def setUp(self):
        self.github=GitHub();self.app=FeedbackApp(GitHubRelay('test-only-secret',request=self.github))
        self.payload={'body':'测试反馈\nBARO feedback','requestId':'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee','version':'0.10.0-preview.6-r2'}

    def call(self,payload=None,**env):
        raw=json.dumps(self.payload if payload is None else payload,ensure_ascii=False).encode();status=[]
        response=self.app({'REQUEST_METHOD':'POST','PATH_INFO':'/v1/feedback','CONTENT_TYPE':'application/json',
            'CONTENT_LENGTH':str(len(raw)),'wsgi.input':io.BytesIO(raw),**env},lambda s,h:status.append(s))
        return int(status[0].split()[0]),json.loads(b''.join(response))

    def test_submit_verifies_issue_and_same_request_reuses_receipt(self):
        code,result=self.call();self.assertEqual(200,code);self.assertTrue(result['accepted'])
        self.assertEqual('https://github.com/dpan538/baro/issues/1',result['receipt'])
        self.assertEqual((code,result),self.call());self.assertEqual(1,self.github.posts)
        self.assertNotIn('test-only-secret',self.github.issues[0]['body'])

    def test_digest_preserves_whitespace_and_binds_the_original_version(self):
        data={**self.payload,'body':'  保留空格\r\nFeedback  '}
        self.assertEqual(200,self.call(data)[0]);digest=hashlib.sha256((data['body']+'\0'+data['version']).encode()).hexdigest()
        self.assertIn('<!-- BARO:'+data['requestId']+':'+digest+' -->',self.github.issues[0]['body'])
        self.assertEqual(409,self.call({**data,'version':'different'})[0])

    def test_timeout_after_write_reconciles_without_second_post(self):
        self.github.fail_after_write=True;self.assertEqual(503,self.call()[0]);self.assertTrue(self.call()[1]['accepted']);self.assertEqual(1,self.github.posts)

    def test_foreign_or_unverified_receipt_never_reports_success(self):
        self.github.invalid_receipt=True;self.assertEqual((502,{'error':'github_response_invalid'}),self.call())
        self.github=GitHub();self.github.failed_verification=True;self.app=FeedbackApp(GitHubRelay('test',request=self.github))
        self.assertEqual((502,{'error':'github_receipt_unverified'}),self.call())

    def test_changed_body_with_same_identity_conflicts(self):
        self.call();self.assertEqual(409,self.call({**self.payload,'body':'different'})[0])

    def test_missing_credentials_and_upstream_failures_do_not_accept(self):
        self.app=FeedbackApp(GitHubRelay(''));self.assertEqual((503,{'error':'service_unconfigured'}),self.call())
        self.app=FeedbackApp(GitHubRelay('test',request=lambda *_:(403,{})));self.assertEqual(503,self.call()[0])

    def test_validation_unicode_unknown_fields_and_content_limits(self):
        for data in ({},{**self.payload,'body':' '},{**self.payload,'body':'字'*1001},{**self.payload,'contact':'private'},
                     {**self.payload,'requestId':'bad'},{**self.payload,'body':'a\0b'}):self.assertEqual(400,self.call(data)[0])
        self.assertEqual(200,self.call({**self.payload,'body':'🌧'*1000})[0])
        self.assertEqual(413,self.call(CONTENT_LENGTH='20000')[0]);self.assertEqual(415,self.call(CONTENT_TYPE='text/plain')[0])
        self.assertEqual(405,self.call(REQUEST_METHOD='DELETE')[0]);self.assertEqual(404,self.call(PATH_INFO='/elsewhere')[0])

    def test_health_is_read_only_and_does_not_disclose_token(self):
        code,result=self.call(REQUEST_METHOD='GET',PATH_INFO='/healthz');self.assertEqual(200,code)
        self.assertEqual({'service':'baro-feedback','schema':1,'configured':True},result);self.assertEqual(0,self.github.posts)

    def test_repository_hourly_guard(self):
        self.call();self.github.issues=self.github.issues*20
        self.assertEqual(429,self.call({**self.payload,'requestId':'bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee'})[0])

    def test_full_reconciliation_window_fails_closed(self):
        self.call();self.github.issues=self.github.issues*500
        self.assertEqual(503,self.call({**self.payload,'requestId':'bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee'})[0]);self.assertEqual(1,self.github.posts)

    def test_real_http_request_receives_verified_issue_receipt(self):
        class Quiet(WSGIRequestHandler):
            def log_message(self,*_):pass
        server=make_server('127.0.0.1',0,self.app,handler_class=Quiet);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        try:
            req=urllib.request.Request(f'http://127.0.0.1:{server.server_port}/v1/feedback',json.dumps(self.payload).encode(),{'Content-Type':'application/json'})
            with urllib.request.urlopen(req) as r:self.assertTrue(json.load(r)['accepted'])
            self.assertEqual(1,self.github.posts)
        finally:server.shutdown();server.server_close();thread.join()


if __name__=='__main__':unittest.main()
