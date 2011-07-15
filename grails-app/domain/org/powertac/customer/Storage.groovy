/*
* Copyright 2009-2010 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an
* "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
* either express or implied. See the License for the specific language
* governing permissions and limitations under the License.
*/

package org.powertac.customer

import java.util.HashMap;

import groovy.util.ConfigObject;
import org.joda.time.Instant;
import org.powertac.common.TariffSubscription;

/**
 * Simple energy storage (battery) that uses cheap timeslots to charge 
 * and expensive timeslots to discharge to save money
 */
class Storage {
	def conf;
	def timeService;
	
	HashMap<String, double[]> dailyStorage = new HashMap<String, double[]>();
	
	def init() {
		this.conf = new ConfigSlurper().parse(new File('../powertac-macro-customer/grails-app/conf/StorageConfig.groovy').toURL());
		
		this.save();
	}
	
	def nextDay(subscriptions) {
		subscriptions.each { sub ->
			this.dailyStorage.put(sub.getTariff().getSpecId(), this.generateStrategy(sub));
		}
	}
	
	def store(sub, wr) {
		return this.dailyStorage.get(sub.getTariff().getSpecId());
	}
	
	// generate and use strategy based on cheap and expensive timeslots	
	def generateStrategy(sub) {
		Instant now = timeService.getCurrentTime();
		
		// read storage parameters
		double efficiency = this.conf.storage.efficiency;
		double capacity = this.conf.storage.capacity;
		
		double maxPrice = Double.MIN_VALUE;
		double minPrice = Double.MAX_VALUE;
		double minPrice2 = Double.MAX_VALUE;
		
		int maxIntervall = -1;
		int minIntervall = -1;
		int minIntervall2 = -1;
		
		// find cheapest / most expensive timeslots
		for(int i = 0; i < 24; i++) {
			Instant time = now + i * 3600000;
			double price = sub.getTariff().getUsageCharge(now, 1.0, 0.0);
			if(price > maxPrice) {
				maxPrice = price;
				maxIntervall = i;
			} else if (price < minPrice) {
				minPrice2 = minPrice;
				minIntervall2 = minIntervall;
				minPrice = price;
				minIntervall = i;
			} else if (price < minPrice2) {
				minPrice2 = price;
				minIntervall2 = i;
			}
		}
		
		double borderPrice = (minPrice + minPrice2) / 2;		

		double[] influence = new double[24];
		
		// price difference high enough for profitability?		
		if(maxPrice > borderPrice) {
			// set influences (charging / discharging)
			influence[maxIntervall] -= capacity;
			influence[minIntervall] += (capacity / efficiency) / 2;
			influence[minIntervall2] += (capacity / efficiency) / 2;
		}
		
		return influence;
	}
}
