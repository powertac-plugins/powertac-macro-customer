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

import org.joda.time.Instant
import org.powertac.common.TariffSubscription

/**
 * Shifts load between timeslots and the previous / next one
 */
class LoadShifting {
	def timeService; //auto-wire
	ConfigObject conf;
	
	public void init(ConfigObject conf) {
		this.conf = conf;
		this.save();
	}
	
	// calculate new loads after shifting
	public double[] shift(double[] consumption, TariffSubscription sub) {	
		double[] newConsumption = consumption;
		for(int i = 0; i < 24; i++) {						
			// shifting back -> only if timeslot > 0
			if(i > 0) {
				newConsumption[i] -= this.shiftBack(i, i - 1, consumption[i], sub);
			}
			
			// shifting back -> only if timeslot < 23
			if(i < 23) {
				newConsumption[i] -= this.shiftForward(i, i + 1, consumption[i], sub);
			}
			
			// load shifted from next timeslot -> only if timeslot < 23
			if(i < 23)
				newConsumption[i] += this.shiftBack(i + 1, i, consumption[i + 1], sub);
			
			// load shifted from previous timeslot -> only if timeslot > 0
			if(i > 0)
				newConsumption[i] += this.shiftForward(i - 1, i, consumption[i - 1], sub);
		}

		return newConsumption;
	}
	
	// shifting from one timeslot to the previous one
	private double shiftBack(int slot1, int slot2, double load, TariffSubscription sub) {
		Instant now = timeService.getCurrentTime() + slot1 * 3600000;

		// get prices
		double price = sub.getTariff().getUsageCharge(now, 1.0, 0.0);	
		double previousPrice = sub.getTariff().getUsageCharge(now - 3600000, 1.0, 0.0);
		double nextPrice = Double.MAX_VALUE;
		if(slot1 != 23)
			nextPrice = sub.getTariff().getUsageCharge(now + 3600000, 1.0, 0.0);
		
		double alpha = this.conf.consumption.priceCharacteristics.loadShifting.(slot1);
		
		double influence = 0;

		// calculate influence based on prices and timeslot
		if(slot1 != 23 && previousPrice < price && nextPrice < price) {
			influence = (1-(previousPrice / (previousPrice + nextPrice))) * alpha * load * Math.pow(((price - previousPrice) / price), 1 - alpha);
		} else if(previousPrice < price) {
			influence = alpha * load * Math.pow(((price - previousPrice) / price), 1 - alpha);
		}
				
		return influence;
	}

	// shifting from one timeslot to the next one
	private double shiftForward(int slot1, int slot2, double load, TariffSubscription sub) {
		Instant now = timeService.getCurrentTime() + slot1 * 3600000;
		
		// get prices
		double price = sub.getTariff().getUsageCharge(now, 1.0, 0.0);
		double previousPrice = Double.MAX_VALUE;
		if(slot1 != 0)
			previousPrice = sub.getTariff().getUsageCharge(now - 3600000, 1.0, 0.0);
		double nextPrice = sub.getTariff().getUsageCharge(now + 3600000, 1.0, 0.0);
		
		double alpha = this.conf.consumption.priceCharacteristics.loadShifting.(slot1);
		
		double influence = 0;
		
		// calculate influence based on prices and timeslot
		if(slot1 != 0 && previousPrice < price && nextPrice < price) {
			influence = (1-(previousPrice / (previousPrice + nextPrice))) * alpha * load * Math.pow(((price - nextPrice) / price), 1 - alpha);
		} else if(nextPrice < price) {
			influence = alpha * load * Math.pow(((price - nextPrice) / price), 1 - alpha);
		}
				
		return influence;
	}
}
