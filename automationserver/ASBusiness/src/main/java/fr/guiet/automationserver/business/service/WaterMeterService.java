package fr.guiet.automationserver.business.service;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Base64;
import org.json.JSONException;
import org.json.JSONObject;

import be.romaincambier.lorawan.FRMPayload;
import be.romaincambier.lorawan.MACPayload;
import be.romaincambier.lorawan.PhyPayload;
import be.romaincambier.lorawan.exceptions.MalformedPacketException;
import fr.guiet.automationserver.business.helper.DateUtils;
import fr.guiet.automationserver.business.helper.LoRaDecrypter;
import fr.guiet.automationserver.dataaccess.DbManager;
import fr.guiet.automationserver.dto.SMSDto;

public class WaterMeterService implements Runnable, IMqttable {

	private boolean _isStopped = false; // Service arrete?
	private static Logger _logger = LogManager.getLogger(WaterMeterService.class);
	private Timer _timer = null;
	private DbManager _dbManager = null;
	private SMSGammuService _smsGammuService = null;
	private MqttService _mqttService = null;
	private ArrayList<String> _mqttTopics = new ArrayList<String>();
	
	private String _pub_topic ="/guiet/automationserver/watermeter";
	
	private String _pub_topic_watermeter = "/guiet/outside/sensor/19";
	
	private final String WATERMETER_SENSOR_ID = "19";
	
	private static String MQTT_TOPIC_LORAWAN="gateway/dca632fffe365d9c/event/up";
	
	private Date _lastMessageReceived = new Date();
	
	public WaterMeterService(SMSGammuService smsGammuService, MqttService mqttService) {
		_smsGammuService = smsGammuService;
		_mqttService = mqttService;
		_dbManager = new DbManager();	
		
		//add topics processed by this service
	 	_mqttTopics.add(MQTT_TOPIC_LORAWAN);
	}
	
	private void CreateWaterMeterInfoTask() {

		TimerTask sendWaterMeterInfoTask = new TimerTask() {
			@Override
			public void run() {
				
				try {
				
				_logger.info("Getting Water Meter info...");

				JSONObject json =  _dbManager.GetWaterMeterInfo();
				
				_mqttService.SendMsg(_pub_topic, json.toString());

				_logger.info("Sending Water Meter info");
				}
				catch (Exception e) {
					_logger.error("Error occured in sendWaterMeterInfoTask...", e);

					SMSDto sms = new SMSDto("ecd763ea-0361-447b-800a-f4ad82e20498");
					sms.setMessage("Error occured in sendWaterMeterInfoTask, review error log for more details");
					_smsGammuService.sendMessage(sms);
				}
			}
		};

		_logger.info("Creating Water Meter Info task");

		_timer = new Timer(true);
		
		//1000 ms = 1s
		_timer.scheduleAtFixedRate(sendWaterMeterInfoTask, 5000, 60000);

	}
	
	@Override
	public void run() {

		_logger.info("Starting WaterMeter...");

		CreateWaterMeterInfoTask();
		
		while (!_isStopped) {

			try {
				
				Thread.sleep(2000);
				
				Long elapsedTime = DateUtils.minutesBetweenDate(_lastMessageReceived, new Date());
				
				if (elapsedTime >= 75) {
					String mess = "Aucune nouvelle du capteur sur compteur d'eau depuis 1h";

					_logger.info(mess);

					SMSDto sms = new SMSDto("9e357202-cb61-4338-9562-df07564460d0");
					sms.setMessage(mess);
					_smsGammuService.sendMessage(sms);
					
					//Reset
					_lastMessageReceived = new Date();
										
				}
				
			} catch (Exception e) {
				_logger.error("Error occured in WaterMeter service...", e);

				SMSDto sms = new SMSDto("166d9b40-30c0-4a14-9a92-c48b0ace0011");
				sms.setMessage("Error occured in WaterMeter services service, review error log for more details");
				_smsGammuService.sendMessage(sms);
			}
		}
	}
	
	// Arret du service LoRaService
	public void StopService() {
		
		if (_timer != null)
			_timer.cancel();

		_logger.info("Stopping WaterMeter service...");

		_isStopped = true;
	}

	@Override
	public boolean ProcessMqttMessage(String topic, String message) {
		
		boolean messageProcessed = false;
		
		if (topic.equals(MQTT_TOPIC_LORAWAN)) {
			
			//_logger.info("LoRaWAN message received : " + message);
			
			String decryptedPayload = "";
			
			try {
				JSONObject json = new JSONObject(message);
				
				String payload = json.getString("phyPayload");
				
				_logger.info("Payload base64 received : " + payload);
				
							    
				PhyPayload pp;
				//try {
				byte[] decode = Base64.decode(payload);
				byte[] nwkSKey = new byte[] { (byte)0xA2, (byte)0xE9, (byte)0xB3, 0x2D, (byte)0xE6, (byte)0xCE, (byte)0xA0, 0x73, 0x70, (byte)0xA5, 0x21, (byte)0xAB, (byte)0xCF, 0x51, (byte)0x88, 0x2A };
			    byte[] appSKey = new byte[] { (byte)0xC3, 0x0B, (byte)0xEA, (byte)0xEE, (byte)0xE8, (byte)0xBA, 0x50, (byte)0xA8, 0x31, 0x5B, 0x1E, 0x05, (byte)0x9B, (byte)0xAA, (byte)0xE0, (byte)0xB8 };
			    				    
				pp = PhyPayload.parse(ByteBuffer.wrap(decode));
				
				MACPayload pl = (MACPayload) pp.getMessage();
				
				FRMPayload data = pl.getFRMPayload();
				byte[] clearData = data.getClearPayLoad(nwkSKey, appSKey);
				
				decryptedPayload = new String(clearData);
				
				_logger.info("Decrypted LoraWAN Message : " + decryptedPayload);
				
				//Try parse payload
				
				String[] messageContent = decryptedPayload.split(" ");
				
				String sensorid = messageContent[0];
				
				if (sensorid.equals(WATERMETER_SENSOR_ID)) {
					
					String firmware  = messageContent[1];	
					String voltage  = messageContent[2];
					String literConsumed  = messageContent[3];
					String literConsumedFromStart  = messageContent[4];
					
					//TODO : review that!
					JSONObject mess = new JSONObject();
					mess.put("id", WATERMETER_SENSOR_ID);
					mess.put("n", "WM"); //WM : Water Meter
					mess.put("f", firmware);
					mess.put("b", voltage);
					mess.put("l", literConsumed);
					mess.put("cft", literConsumedFromStart);
					
					_logger.info("JSON Message for OpenHab : " + mess.toString());
					
					ManageWaterMeterSensor(mess.toString(), voltage, literConsumed);
				}
				else {
					throw new Exception("sensorid is " + sensorid + ", mine is " + WATERMETER_SENSOR_ID + ", LoRaWAN frame not for me");
				}
					
				/*} catch (MalformedPacketException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/
				
				
				messageProcessed = true;
				
			}
			catch(JSONException e) {			
				_logger.info("Cannot parse JSON LoRaWAN Message : " + message, e);				
			}
			catch(Exception e) {
				_logger.info("Cannot parse LoRaWAN decrypted message, should not be for me : " + decryptedPayload, e);
			}
		}
				
		return messageProcessed;
	}
	
	private void ManageWaterMeterSensor(String message, String battery, String liter) {
		
		_mqttService.SendMsg(_pub_topic_watermeter, message);
		
		float vcc = 0;
		try {
			vcc = Float.parseFloat(battery);
		}
		catch(NumberFormatException e) {
			_logger.error("Valeur de la batterie pour le capter Water Meter incorrecte : " + battery);
		}
		
		int consumption = 0;
		try {
			consumption = Integer.parseInt(liter);
		}
		catch(NumberFormatException e) {
			_logger.error("Valeur de la consommation pour le capter Water Meter incorrecte : " + battery);
		}
		
		_logger.info("WaterMeter voltage " + vcc + ", consumption : " + consumption);
		
		_dbManager.SaveWaterMeterInfo(vcc, consumption);
	}

	@Override
	public ArrayList<String> getTopics() {
		return _mqttTopics;
	}
}	
