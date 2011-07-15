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

import org.joda.time.Instant
import groovy.util.ConfigObject;
import org.powertac.common.WeatherReport;
import org.powertac.common.AbstractCustomer;
import org.powertac.common.*;
import org.powertac.common.enumerations.PowerType;
import java.util.HashMap;
import java.util.ArrayList;


import org.powertac.common.TariffSubscription;

/**
 * Essential class for this model. Controls tariffs, consumtpion calculation etc.
 */
class MacroCustomer extends AbstractCustomer {
	def randomSeedService; //auto-wire	
	def weatherService; //auto-wire
	
	def consumption;
	def storage;
	def production;
	
	def initialized;
	
	ConfigObject conf;	
		
	// Last weather report (broadcasted in phase 3, received via MacroCustomerService)
	WeatherReport lastWR;

	@Override
	void init() {
		// set customer id
		this.custId = customerInfo.getId();
	  
		// load configuration
		PluginConfig macroCustomerConfig = PluginConfig.findByRoleName('MacroCustomer');
		this.conf = new ConfigSlurper().parse(new File(macroCustomerConfig.configuration['configFile'].toString()).toURL());
			
		// generate fake WeatherReport because first WeatherReport wasn't broadcasted yet
		this.lastWR = new WeatherReport(temperature: 10.0, cloudCover: 0.0)
		this.lastWR.save();
		
		this.production = new Production();
		this.storage = new Storage();
		this.consumption = new Consumption();
		
		this.consumption.init();
		this.production.init();
		this.storage.init();
		
		this.initialized = true;
				
		this.save();
	}
	
	@Override
	void step(){
		// ugly workaround
		if(!this.initialized)
			this.init()
			
		// new day? -> generate consumption, look for better tariffs
		if(timeService.getHourOfDay() == 0) {
			//this.chooseTariff();
					
			consumption.nextDay(subscriptions);
			production.nextDay(subscriptions);
			storage.nextDay(subscriptions);
			
		}
		this.checkRevokedSubscriptions();
		this.consume();
	}
	
	@Override
	void consume() {
		production.registerWeatherReport(this.lastWR)
		
		// consume power for every tariff 
		subscriptions.each { sub ->			
			double c = 0;	
			if(this.conf.macro.consumption) {				
				c = consumption.consume(sub, this.lastWR);
			}
						
			double p = 0;
			if(this.conf.macro.production)
				p = production.produce(sub);
				
			double s = 0;
			if(this.conf.macro.storage)	
				s = storage.store(sub, this.lastWR);
				
			double cInfluence = sub.getCustomersCommitted() * c;
			double pInfluence = (sub.getCustomersCommitted() / this.getPopulation()) * this.conf.macro.populationWG * p;
			double sInfluence = (sub.getCustomersCommitted() / this.getPopulation()) * this.conf.macro.populationWS * s
				
			double load = cInfluence - pInfluence + sInfluence;
			
			log.info("Consumption Load - Tariff " + sub.getTariff().getSpecId() + ": ${load} (${cInfluence}, ${pInfluence}, ${sInfluence})");
			sub.usePower(load);
		}
	}
	
	/**
	 * Ugly hack: register weather in phase 3, next timeslot's phase 1
	 * Reason: error during TariffSubscription.usePower() when MacroCustomerService is 
	 * registered for phase 3 and all stuff is done at that time 
	 */
	void registerWeather() {
		this.lastWR = WeatherReport.findByCurrentTimeslot(Timeslot.currentTimeslot());		
	}
	
	/**
	 *  create bootstrap data based on randomized load profiles
	 */
	def getBootstrapData() {
		return consumption.getBootstrapData(this.getPopulation());
	}	
	
	/**
	 * catch calls
	 */
	@Override
	void possibilityEvaluationNewTariffs(List<Tariff> newTariffs) {
	}
	
	/**
	 *  Tariff choice (based on game spec; modificated) 
	 */
	/*void chooseTariff() {
		List<Tariff> newTariffs = Tariff.findAllByState(Tariff.State.OFFERED);
		
		// if there are no current subscriptions, then this is the
		// initial publication of default tariffs
		if (subscriptions == null || subscriptions.size() == 0) {
		  subscribeDefault()
		  return
		}
		log.info "Tariffs: ${Tariff.list().toString()}"
		Vector estimation = new Vector()
	
		//adds current subscribed tariffs for reevaluation
		def evaluationTariffs = new ArrayList(newTariffs)
		Collections.copy(evaluationTariffs,newTariffs)
		evaluationTariffs.addAll(subscriptions?.tariff)
	
		log.debug("Estimation size for ${this.toString()}= " + evaluationTariffs.size())
		if (evaluationTariffs.size()> 1) {
		  evaluationTariffs.each { tariff ->
			log.info "Tariff : ${tariff.toString()} Tariff Type : ${tariff.powerType} Tariff Expired : ${tariff.isExpired()}"
			if (!tariff.isExpired() && customerInfo.powerTypes.find{tariff.powerType == it}) {
			  estimation.add(-(costEstimation(tariff)))
			}
			else estimation.add(Double.NEGATIVE_INFINITY)
		  }
		  int minIndex = logitPossibilityEstimation(estimation)
	
		  subscriptions.each { sub ->
			log.info "Equality: ${sub.tariff.tariffSpec} = ${evaluationTariffs.getAt(minIndex).tariffSpec} "
			if (!(sub.tariff.tariffSpec == evaluationTariffs.getAt(minIndex).tariffSpec)) {
			  log.info "Existing subscription ${sub.toString()}"
			  int populationCount = sub.customersCommitted
			  this.unsubscribe(sub, populationCount)
			  this.subscribe(evaluationTariffs.getAt(minIndex),  populationCount)
			}
		  }
		  this.save()
		}*/
	
	/*
		HashMap<Tariff, Double> costs = new HashMap<Tariff, Double>();
		
		// find all offered tariffs, caluclate costs
		Tariff.findAllByState(Tariff.State.OFFERED).each { tariff ->
			System.out.println("Evaluating: " + tariff.getSpecId());
			if (tariff.getPowerType().equals(PowerType.CONSUMPTION)) {
				// assume expected tariff life of 2 days
				double c = tariff.getPeriodicPayment() + (tariff.getSignupPayment() / 48);
				
				double[] consumption = consumption.generateBaseConsumption();				
				
				// sum up variable costs
				for(int i = 0; i < consumption.length; i++) {
					Instant now = timeService.getCurrentTime() + i * 3600000;
					c += consumption[i] * tariff.getUsageCharge(now, 1.0, 0.0);
				}
				
				costs.put(tariff, c);
			}
		}
		
		// sum up ranking values of all tariffs as a base for the propabilities		
		double sum = 0;
		double lambda = 5;
		
		for(Tariff t : costs.keySet()) {
			sum += Math.exp(lambda * costs.get(t));
		}
		
		// calculate customer count for every tariff based on propabilities
		for(Tariff t : costs.keySet()) {
			// caluclate propapility, customer count
			double propability = (Math.exp(lambda * costs.get(t)) / sum);
			System.out.println(propability);
			int consumers = (int) (this.getPopulation() * propability);
			
			int registeredConsumers = 0; 
			// tariff ist already registered? -> get committed customer count
			if(TariffSubscription.findByTariffAndCustomer(t, this) != null) {
				registeredConsumers = TariffSubscription.findByTariffAndCustomer(t, this).getCustomersCommitted();				
			}
			
			// how many customers have to be added / unsubscribed
			int diff = consumers - registeredConsumers;
					
			if(diff > 0) {
				// add customers
				this.addToSubscriptions(tariffMarketService.subscribeToTariff(t, this, diff))
				log.info("subscribing " + diff);
			} else if(diff < 0){
				// unsubscribe customers 
				TariffSubscription.findByTariffAndCustomer(t, this).unsubscribe(-diff);
				log.info("unsubscribing " + diff);
			}			
		}
	
	}*/
}