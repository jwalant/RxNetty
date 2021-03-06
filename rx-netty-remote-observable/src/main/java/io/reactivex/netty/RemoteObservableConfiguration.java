package io.reactivex.netty;

import io.reactivex.netty.codec.Encoder;
import io.reactivex.netty.filter.ServerSideFilters;
import io.reactivex.netty.slotting.SlottingStrategies;
import io.reactivex.netty.slotting.SlottingStrategy;

import java.util.Map;

import rx.Observable;
import rx.functions.Func1;

public class RemoteObservableConfiguration<T> {

	private String name;
	private Observable<T> observable;
	private SlottingStrategy<T> slottingStrategy;
	private Encoder<T> encoder;
	private Func1<Map<String,String>, Func1<T,Boolean>> filterFunction;
	
	public RemoteObservableConfiguration(Builder<T> builder){
		this.name = builder.name;
		this.observable = builder.observable;
		this.slottingStrategy = builder.slottingStrategy;
		this.encoder = builder.encoder;
		this.filterFunction = builder.filterFunction;
	}

	String getName() {
		return name;
	}

	Observable<T> getObservable() {
		return observable;
	}

	SlottingStrategy<T> getSlottingStrategy() {
		return slottingStrategy;
	}

	Encoder<T> getEncoder() {
		return encoder;
	}
	
	Func1<Map<String, String>, Func1<T, Boolean>> getFilterFunction() {
		return filterFunction;
	}

	public static class Builder<T>{
		
		private String name;
		private Observable<T> observable;
		private SlottingStrategy<T> slottingStrategy = SlottingStrategies.noSlotting();
		private Encoder<T> encoder;
		private Func1<Map<String,String>, Func1<T,Boolean>> filterFunction = ServerSideFilters.noFiltering();

		public Builder<T> name(String name){
			if (name != null && name.length() > 127){
				throw new IllegalArgumentException("Observable name must be less than 127 characters");
			}
			this.name = name;
			return this;
		}
		
		public Builder<T> observable(Observable<T> observable){
			this.observable = observable;
			return this;
		}
		
		public Builder<T> slottingStrategy(SlottingStrategy<T> slottingStrategy){
			this.slottingStrategy = slottingStrategy;
			return this;
		}
		
		public Builder<T> encoder(Encoder<T> encoder){
			this.encoder = encoder;
			return this;
		}
		
		public Builder<T> serverSideFilter(
				Func1<Map<String, String>, Func1<T, Boolean>> filterFunc) {
			this.filterFunction = filterFunc;
			return this;
		}
		
		public RemoteObservableConfiguration<T> build(){
			return new RemoteObservableConfiguration<T>(this);
		}
	}
}
