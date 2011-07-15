/*
* Copyright 2011 the original author or authors.
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

import org.powertac.common.TariffSubscription;

/**
 * Calculates decrease / increase of the consumption because of high / low prices
 */
class LoadAvoidance {
	ConfigObject conf;
	
	// exponentially smoothed average
	HashMap<String, Double> priceAverage = new HashMap<String, Double>();
	
	public void init(ConfigObject conf) {
		this.conf = conf;
		this.save();
	}

	// decrease / increase calculation	
	public double avoid(double consumption, TariffSubscription sub) {
		double influence;
		double price = sub.getTariff().getUsageCharge(1.0, 0.0, false);
		
		// use average price and elasticity to calculate decrease / increase
		double av = this.calcAverage(price, sub);
		double elasticity = this.conf.consumption.priceCharacteristics.loadAvoidance.elasticity;
		influence = (price - av) * elasticity * consumption;

		
		return influence;
	}
	
	// average calculation
	private double calcAverage(double price, TariffSubscription sub) {
		double average;
		if(!this.priceAverage.containsKey((sub.getTariff().getSpecId()))) {
			average = price;
		} else {
			// calculate exponentially smoothed average (recursive)
			double alpha = this.conf.consumption.priceCharacteristics.loadAvoidance.alpha;
			double oldAv = this.priceAverage.get(sub.getTariff().getSpecId());
			average = alpha * price + (1 - alpha) * oldAv;
		}
		
		this.priceAverage.put(sub.getTariff().getSpecId(), average);
		
		return average;
	}
}
