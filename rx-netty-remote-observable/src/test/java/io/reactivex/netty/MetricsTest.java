package io.reactivex.netty;

import io.reactivex.netty.codec.Codecs;

import org.junit.Assert;
import org.junit.Test;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Action1;

public class MetricsTest {
	
	@Test
	public void testConnectionMetrics(){
		// setup
		Observable<Integer> os = Observable.range(1, 1000);
		// serve
		PortSelectorWithinRange portSelector = new PortSelectorWithinRange(8000, 9000);
		int serverPort = portSelector.acquirePort();
		RemoteRxServer server = RemoteObservable.serve(serverPort, os, Codecs.integer());
		server.start();
		// connect
		ConnectConfiguration<Integer> cc = new ConnectConfiguration.Builder<Integer>()
				.host("localhost")
				.port(serverPort)
				.decoder(Codecs.integer())
				.build();
		
		RemoteRxConnection<Integer> rc = RemoteObservable.connect(cc);
		// assert
		Observable.sumInteger(rc.getObservable()).toBlockingObservable().forEach(new Action1<Integer>(){
			@Override
			public void call(Integer t1) {
				Assert.assertEquals(500500, t1.intValue()); // sum of number 0-100
			}
		});
		
		Assert.assertEquals(1000, rc.getMetrics().getOnNextCount());
		Assert.assertEquals(0, rc.getMetrics().getOnErrorCount());
		Assert.assertEquals(1, rc.getMetrics().getOnCompletedCount());
	}
	
	@Test
	public void testServerMetrics(){
		// setup
		Observable<Integer> os = Observable.range(1, 1000);
		// serve
		PortSelectorWithinRange portSelector = new PortSelectorWithinRange(8000, 9000);
		int serverPort = portSelector.acquirePort();
		RemoteRxServer server = RemoteObservable.serve(serverPort, os, Codecs.integer());
		server.start();
		// connect
		ConnectConfiguration<Integer> cc = new ConnectConfiguration.Builder<Integer>()
				.host("localhost")
				.port(serverPort)
				.decoder(Codecs.integer())
				.build();
		
		Observable<Integer> oc = RemoteObservable.connect(cc).getObservable();
		// assert
		Observable.sumInteger(oc).toBlockingObservable().forEach(new Action1<Integer>(){
			@Override
			public void call(Integer t1) {
				Assert.assertEquals(500500, t1.intValue()); // sum of number 0-100
			}
		});
		
		Assert.assertEquals(1000, server.getMetrics().getOnNextCount());
		Assert.assertEquals(0, server.getMetrics().getOnErrorCount());
		Assert.assertEquals(1, server.getMetrics().getOnCompletedCount());
	}
	
	@Test
	public void testMutlipleConnectionsSingleServerMetrics() throws InterruptedException{
		// setup
		Observable<Integer> os = Observable.range(1, 1000);
		// serve
		PortSelectorWithinRange portSelector = new PortSelectorWithinRange(8000, 9000);
		int serverPort = portSelector.acquirePort();
		RemoteRxServer server = RemoteObservable.serve(serverPort, os, Codecs.integer());
		server.start();
		// connect
		ConnectConfiguration<Integer> cc = new ConnectConfiguration.Builder<Integer>()
				.host("localhost")
				.port(serverPort)
				.decoder(Codecs.integer())
				.build();
		
		RemoteRxConnection<Integer> ro1 = RemoteObservable.connect(cc);
		// assert
		Observable.sumInteger(ro1.getObservable()).toBlockingObservable().forEach(new Action1<Integer>(){
			@Override
			public void call(Integer t1) {
				Assert.assertEquals(500500, t1.intValue()); // sum of number 0-100
			}
		});
		
		RemoteRxConnection<Integer> ro2 = RemoteObservable.connect(cc);
		// assert
		Observable.sumInteger(ro2.getObservable()).toBlockingObservable().forEach(new Action1<Integer>(){
			@Override
			public void call(Integer t1) {
				Assert.assertEquals(500500, t1.intValue()); // sum of number 0-100
			}
		});
		
		// client asserts
		Assert.assertEquals(1000, ro1.getMetrics().getOnNextCount());
		Assert.assertEquals(0, ro1.getMetrics().getOnErrorCount());
		Assert.assertEquals(1, ro1.getMetrics().getOnCompletedCount());
		
		Assert.assertEquals(1000, ro2.getMetrics().getOnNextCount());
		Assert.assertEquals(0, ro2.getMetrics().getOnErrorCount());
		Assert.assertEquals(1, ro2.getMetrics().getOnCompletedCount());
		
		// server asserts
		Assert.assertEquals(2000, server.getMetrics().getOnNextCount());
		Assert.assertEquals(0, server.getMetrics().getOnErrorCount());
		Assert.assertEquals(2, server.getMetrics().getOnCompletedCount());
		Assert.assertEquals(2, server.getMetrics().getSubscribedCount());		
		Thread.sleep(1000); // allow time for unsub, connections to close
		Assert.assertEquals(2, server.getMetrics().getUnsubscribedCount());
	}
	
	@Test
	public void testMutlipleConnectionsSingleServerErrorsMetrics() throws InterruptedException{
		// setup
		Observable<Integer> o = Observable.create(new OnSubscribe<Integer>(){
			@Override
			public void call(Subscriber<? super Integer> subscriber) {
				for(int i=0; i<10; i++){
					if (i == 5){
						subscriber.onError(new RuntimeException("error"));
					}
					subscriber.onNext(i);
				}
			}
		});
		// serve
		PortSelectorWithinRange portSelector = new PortSelectorWithinRange(8000, 9000);
		int serverPort = portSelector.acquirePort();
		RemoteRxServer server = RemoteObservable.serve(serverPort, o, Codecs.integer());
		server.start();
		// connect
		ConnectConfiguration<Integer> cc = new ConnectConfiguration.Builder<Integer>()
				.host("localhost")
				.port(serverPort)
				.decoder(Codecs.integer())
				.build();
		
		RemoteRxConnection<Integer> ro1 = RemoteObservable.connect(cc);
		try{
			Observable.sumInteger(ro1.getObservable()).toBlockingObservable().forEach(new Action1<Integer>(){
				@Override
				public void call(Integer t1) {
					Assert.assertEquals(500500, t1.intValue()); // sum of number 0-100
				}
			});
		}catch(Exception e){
			// noOp
		}
		
		RemoteRxConnection<Integer> ro2 = RemoteObservable.connect(cc);
		try{
			Observable.sumInteger(ro2.getObservable()).toBlockingObservable().forEach(new Action1<Integer>(){
				@Override
				public void call(Integer t1) {
					Assert.assertEquals(500500, t1.intValue()); // sum of number 0-100
				}
			});
		}catch(Exception e){
			// noOp
		}
		
		// client asserts
		Assert.assertEquals(5, ro1.getMetrics().getOnNextCount());
		Assert.assertEquals(1, ro1.getMetrics().getOnErrorCount());
		Assert.assertEquals(0, ro1.getMetrics().getOnCompletedCount());
		
		Assert.assertEquals(5, ro2.getMetrics().getOnNextCount());
		Assert.assertEquals(1, ro2.getMetrics().getOnErrorCount());
		Assert.assertEquals(0, ro2.getMetrics().getOnCompletedCount());
		
		// server asserts
		Assert.assertEquals(10, server.getMetrics().getOnNextCount());
		Assert.assertEquals(2, server.getMetrics().getOnErrorCount());
		Assert.assertEquals(0, server.getMetrics().getOnCompletedCount());
		Assert.assertEquals(2, server.getMetrics().getSubscribedCount());		
		Thread.sleep(1000); // allow time for unsub, connections to close
		Assert.assertEquals(2, server.getMetrics().getUnsubscribedCount());
	}
	
	@Test
	public void testConnectionOnErrorCount(){
		// setup
		Observable<Integer> o = Observable.create(new OnSubscribe<Integer>(){
			@Override
			public void call(Subscriber<? super Integer> subscriber) {
				for(int i=0; i<10; i++){
					if (i == 5){
						subscriber.onError(new RuntimeException("error"));
					}
					subscriber.onNext(i);
				}
			}
		});
		// serve
		PortSelectorWithinRange portSelector = new PortSelectorWithinRange(8000, 9000);
		int serverPort = portSelector.acquirePort();
		RemoteRxServer server = RemoteObservable.serve(serverPort, o, Codecs.integer());
		server.start();
		// connect
		ConnectConfiguration<Integer> cc = new ConnectConfiguration.Builder<Integer>()
				.host("localhost")
				.port(serverPort)
				.decoder(Codecs.integer())
				.build();
		
		RemoteRxConnection<Integer> rc = RemoteObservable.connect(cc);
		
		// assert
		try{
			Observable.sumInteger(rc.getObservable()).toBlockingObservable().forEach(new Action1<Integer>(){
				@Override
				public void call(Integer t1) {
					Assert.assertEquals(500500, t1.intValue()); // sum of number 0-100
				}
			});
		}catch(Exception e){
			// noOp
		}
		
		Assert.assertEquals(5, rc.getMetrics().getOnNextCount());
		Assert.assertEquals(1, rc.getMetrics().getOnErrorCount());
		Assert.assertEquals(0, rc.getMetrics().getOnCompletedCount());
	}

}
