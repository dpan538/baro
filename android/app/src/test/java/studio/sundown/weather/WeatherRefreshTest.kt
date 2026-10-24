package studio.sundown.weather

import org.junit.Test
import org.junit.Assert.*

class WeatherRefreshTest {
    private fun reading(at:Long)=Weather(Place("Test",0.0,0.0),at,at,emptyMap(),Series(emptyList(),emptyMap()),emptyList(),null,null,null)
    @Test fun activeRefreshIsDueAtFifteenMinutesBeforeCacheExpiry(){
        var wall=10000L;var elapsed=0L;val s=WeatherRefreshSchedule({wall},{elapsed});val wx=reading(wall)
        assertTrue(s.due());s.started();assertFalse(s.due());s.finished(wx,false)
        wall+=899;elapsed+=899;assertFalse(s.due());assertFalse(wx.stale(wall))
        wall++;elapsed++;assertTrue(s.due());assertFalse(wx.stale(wall))
    }
    @Test fun cachedResumeDoesNotPostponeAnAlreadyDueRefresh(){
        var wall=10000L;var elapsed=0L;val s=WeatherRefreshSchedule({wall},{elapsed});val wx=reading(wall-899)
        s.started();s.finished(wx,false);assertFalse(s.due());wall++;elapsed++;assertTrue(s.due())
        s.started();s.finished(wx,false);assertTrue(s.due())
    }
    @Test fun retryAndDeadlineUseElapsedTimeWhenTheSystemClockMovesBack(){
        var wall=10000L;var elapsed=0L;val s=WeatherRefreshSchedule({wall},{elapsed})
        s.started();elapsed=44;wall-=3600;assertFalse(s.timedOut());elapsed++;assertTrue(s.timedOut())
        s.finished(null,true);elapsed+=299;assertFalse(s.due());elapsed++;assertTrue(s.due())
        s.started();s.paused();assertTrue(s.due())
    }
    @Test fun failedScheduledRefreshDoesNotLoopOnAnAvailableReading(){
        var wall=10000L;var elapsed=0L;val s=WeatherRefreshSchedule({wall},{elapsed});val wx=reading(wall-900)
        s.started();s.finished(wx,true);repeat(299){wall++;elapsed++;assertFalse(s.due())};wall++;elapsed++;assertTrue(s.due())
    }
    @Test fun oldProviderTimestampsAndFutureClockValuesCannotRenewCacheValidity(){
        val wx=reading(10000)
        assertFalse(wx.stale(11799));assertTrue(wx.stale(11800))
        assertTrue(wx.copy(fetchedAt=11800).stale(11800))
        assertTrue(wx.copy(validAt=11800).stale(11800))
        assertTrue(wx.copy(serverStale=true).stale(10000))
        assertTrue(wx.copy(validAt=10301).stale(10000))
    }
}
