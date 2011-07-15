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

package org.powertac.customer;

import org.powertac.common.WeatherReport

/**
 * Calculates the influence of the weather (temperature) on energy consumption
 */
public class WeatherInfluence {
	public static double influence(double load, double baseLoad, WeatherReport wr, int hour, ConfigObject conf) {
		// read temperature
		double temperature = (double) wr.getTemperature();		
		
		// read data for load estimation 
		double a = conf.consumption.weatherCharacteristics.("slot" + hour).a;
		double b = conf.consumption.weatherCharacteristics.("slot" + hour).b;
		
		// estimate load based on temperature, calculate influence
		double loadEstimation = a * temperature + b;
		return 0.5 * (loadEstimation / baseLoad - 1) * load;
	}
}
