package org.powertac.customer

import groovy.util.ConfigObject;

import java.util.HashMap;

import org.powertac.common.TariffSubscription;
import org.powertac.common.WeatherReport
import org.powertac.common.enumerations.PowerType;


class Consumption {
	def timeService;
	def conf;
	def loadAvoidance;	
	
	HashMap<String, double[]> dailyConsumption = new HashMap<String, double[]>();
	
	public void init() {
		this.conf = new ConfigSlurper().parse(new File('../powertac-macro-customer/grails-app/conf/ConsumptionConfig.groovy').toURL());		
		this.loadAvoidance = new LoadAvoidance();
		this.loadAvoidance.init(this.conf);
		
		this.save();
	}
	
	/**
	* generate complete consumption for the whole day
	*/
   void nextDay(subscriptions) {
	   subscriptions.each { sub ->
		   if(sub.getTariff().getPowerType() == PowerType.CONSUMPTION) {			   
			   double[] consumption = this.generateBaseConsumption();

			   // avoid load in expensive timeslots
			   for(int i = 0; i < 24; i++) {
				   consumption[i] += this.loadAvoidance.avoid(consumption[i], sub);
			   }
			   
			   // shift load from expensive to cheap timeslots
			   LoadShifting loadShifting = new LoadShifting();
			   loadShifting.init(this.conf);
			   this.dailyConsumption.put(sub.getTariff().getSpecId(), loadShifting.shift(consumption, sub));			   
		   }
	   }
   }
   
   /**
	* generate randomized load profile
	*/
   double[] generateBaseConsumption() {
	   double[] baseConsumption = new double[24];
	   Distortion distortion = new Distortion();
	   distortion.init(this.conf);
	   
	   // read and distort base consumption for 24 hours (1 day)
	   for(int i = 0; i < 24; i++) {
		   // get load profile
		   double load = BasicConsumption.consumption(i, this.conf);
		   
		   // apply distortion
		   load += distortion.distort(load);
		   
		   baseConsumption[i] = load;
	   }
	   
	   return baseConsumption;
   }
      
   /**
   * calculates the power that has been used for one single tariff
   */
  public double consume(TariffSubscription sub, WeatherReport wr) {
	  int hour = timeService.getHourOfDay();
	  double load = this.dailyConsumption.get(sub.getTariff().getSpecId())[hour];
	  double baseLoad = BasicConsumption.consumption(hour, this.conf)
	  
	  // apply weather influence
	  load += WeatherInfluence.influence(load, baseLoad, wr, hour, this.conf)
	 	  
	  return load;
  }
   
   /**
   *  create bootstrap data based on randomized load profiles
   */
  def getBootstrapData(int population) {
	  def bootstrapConsumption = new double[14][24];
	  for(int i = 0; i < 14; i++) {
		  // randomized load profile for every single day
		  double[] bc = this.generateBaseConsumption()
		  for(int j = 0; j < 24; j++) {			  
			  bootstrapConsumption[i][j] = bc[j] * population;;
		  }
	  }
	  return bootstrapConsumption;
  }
  
  /*def getBootstrapData(int population) {
	  def bootstrapConsumption = new long[14][24];
	  for(int i = 0; i < 14; i++) {
		  // randomized load profile for every single day
		  double[] bc = this.generateBaseConsumption()
		  for(int j = 0; j < 24; j++) {
			  long load = (new Double(bc[j] * population)).longValue();
			  bootstrapConsumption[i][j] = load;
		  }
	  }
	  return bootstrapConsumption;
  }*/
 }
