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

import groovy.util.ConfigObject;

import org.powertac.common.WeatherReport;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Calculate solar electricity generation
 */
class Production {
	def timeService;	
	  
	ArrayList<Double> temps = new ArrayList<Double>();
	ConfigObject conf;
	
	WeatherReport lastWR;
	
	public void init(ConfigObject conf) {
		this.conf = new ConfigSlurper().parse(new File('../powertac-macro-customer/grails-app/conf/ProductionConfig.groovy').toURL());
		
		this.save();
	}
	
	def nextDay(subscription) {
		
	}
	
	def registerWeatherReport(WeatherReport wr) {
		this.lastWR = wr;
		
		this.temps.add(wr.getTemperature());
		
		// delete oldest temperature if necessary
		if(this.temps.size() > 24)
			this.temps.remove(0);
	}
	
	// calculate production based on time and weather
	public double produce(sub) {
		int hour = timeService.getHourOfDay();
		
		// read relevant data from weather report
		double cloudCover = (double) lastWR.getCloudCover();
		double temperature = (double) lastWR.getTemperature();
		
		// calculate solarization
		double solarization = this.solarization(hour);
		
		// read config data
		double efficiency = this.conf.production.efficiency;
		double area = this.conf.production.avArea;
		
		// calculate producation based on weather data, config data and solarization
		double production = area * efficiency * (1 - 0.9 * cloudCover)* solarization;
		
		return Math.max(0, production);
	}
	
	// calculate solarization as input for solar electricity generation
	private double solarization(hour) {
		// average temperature as indicator for winter / summer
		double avgTemp = this.calcAverageTemp();
		boolean winter = false;
		if(avgTemp <= 8)
			winter = true;
		
		double solarization = 0;
			
		// calculate based on time and season
		if(winter) {
			if(hour > 7 && hour < 17) {
				solarization = (1000 - 12.34 * Math.pow(12 - hour, 2)) / 1000;
			}
		} else {
			if(hour > 3 && hour < 21) {
				solarization = (400 - 15 * Math.pow(12 - hour, 2)) / 1000;
			}
		}
		
		return solarization;
	}
	
	// calculate average of last 24 temperatures
	private double calcAverageTemp() {	
		// calculate average
		double sum = 0;
		for(double t : this.temps)
			sum += t;
		return sum / this.temps.size();
	}
}
