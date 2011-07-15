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

package powertac.macro.customer

import java.util.Map;
import java.util.Random

import org.joda.time.Instant
import org.powertac.common.CustomerInfo
import org.powertac.common.PluginConfig
import org.powertac.common.enumerations.CustomerType
import org.powertac.common.enumerations.PowerType
import org.powertac.common.interfaces.NewTariffListener
import org.powertac.common.interfaces.TimeslotPhaseProcessor
import org.powertac.customer.MacroCustomer
import org.powertac.abstractcustomerservice.AbstractCustomerService

/**
 * MacroCustomerService initializes MacroCustomer and receives Phase 3 calls
 * to read weather reports
 */
class MacroCustomerService implements TimeslotPhaseProcessor {
  static transactional = false;

  def timeService; // autowire
  def competitionControlService; // autowire
  def tariffMarketService; // autowire
  
  // the managed customer object
  MacroCustomer customer;

  @Override
  void init(PluginConfig config) {
	// parse config file
	ConfigObject conf = new ConfigSlurper().parse(new File(config.configuration['configFile'].toString()).toURL());	
	
	// create customer info
	int pop = conf.macro.population;
	def info = new CustomerInfo(name: "MacroCustomer",customerType: CustomerType.CustomerHousehold, powerTypes: [PowerType.CONSUMPTION], population: pop, multiContracting: true);
		
	// save and create customer
	assert(info.save());
	this.customer = new MacroCustomer(customerInfo: info);
     
	// init customer and subscribe to default tariff
	this.customer.init();
    this.customer.subscribeDefault();
    assert(this.customer.save());
	
	// register for phase3 to read new weather report
	competitionControlService?.registerTimeslotPhase(this, 3);
  }
   
  @Override
  public void activate (Instant time, int phaseNumber) {
	// only react to phase 3 calls (MacroCustomer is called in phase 1 via AbstractCustomer)
	//if(phaseNumber == 3) this.customer.registerWeather();
	if(phaseNumber == 3)
		MacroCustomer.list()*.registerWeather();
  }  
}