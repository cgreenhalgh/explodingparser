import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import uk.me.jstott.jcoord.LatLng;

public class Parser {

	private static final String directory = "clientlogs";
	private static final String separator = System.getProperty("file.separator");
	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

	public static void main(String argv[]){
		parseFiles();
	}

	static String cleanClientJSON(String json){
		// any [name]?
		int ix = 0;
		while (ix>=0 && ix<json.length()) {
			ix = json.indexOf('[', ix);
			if (ix<0)
				break;
			int ix2 = json.indexOf(']', ix);
			if (ix2<0)
				break;
			boolean replace = true;
			for (int i=ix+1; replace && i<ix2; i++) 
				if (!Character.isLetterOrDigit(json.charAt(i)))
					replace = false;
			if (replace && ix2>ix+2) {
				if (ix<0 || ix2<1 || ix+2>ix2 || ix2+1>=json.length())
					System.err.println("Problem!");
				else
					json = json.substring(0, ix)+json.substring(ix+1, ix2-1)+json.substring(ix2+1);
			}
			ix++;
			//ix = ix2+1;
		}
		int index = json.indexOf("message");
		if (index>=0)
			return json.substring(0, index)+"msg"+json.substring(index+"message".length());
//		String tail = json.substring(index+"message".length());
//		tail = tail.replace("message", "msg");
//		String newjson = json.substring(0, index+"message".length())  + tail; 
//		return newjson;
		return json;
	}

	static String cleanClientState(String json, BufferedReader in){
		if (json.contains("ERROR_AFTER_STATE") || json.contains("ERROR_DOING_LOGIN") || json.contains("ERROR_IN_SERVER_URL") || json.contains("ERROR_GETTING_STATE")){
			try{
				String line;
				StringBuilder remaining = new StringBuilder();
				do{
					line = in.readLine();
					if (line==null) 
						break;
					remaining.append(line.trim());
				}while(!( line.trim().substring(line.length()-1, line.length()).equals("]")) || line.contains("["));
				
				json = json + remaining + "]";
			}catch(Exception e){
				
			}
		}
		json = json.replace("ClientState:", "ClientState:\"");
		json += "\"";
		return json;
	}

	
	static JSONObject parseIt(String json) throws JSONException {
//		try{
			JSONObject obj = new JSONObject(new JSONTokener(json));
			/*Iterator keys = obj.keys();
			while (keys.hasNext()){
				System.err.println(keys.next());
			}*/
			return obj;
//		}catch(JSONException e){
//			System.err.println("json parse exception " + e.getMessage());
//			System.err.println("json is " + json);
//		}catch(Exception e){
//			System.err.println("general exception " + e.getMessage());
//			System.err.println("json is " + json);
//		}
//		return null;
	}
	
	
	static String getLocHeaders() {
		return "gametime,zone,lon,lat,age,r,dist,";
	}
	static long locTime = 0;
	static double locLatitude = 0;
	static double locLongitude = 0;
	// persistent between logs
	static String lastPlayer = "";
	static double lastLatitude = 0;
	static double lastLongitude = 0;
	static long lastTime = 0;
	static double locAccuracy = 0;
	static double refLongitude = 0.067987;
	static double refLatitude = 51.489499;
	// last reported
	static String lastZone = "";
	static String getTime(int day,double hour,long time,String gameId) {
		// known game start times?
		long timeZero = 0;
		if (gameId.equals("GA522")) {
			// TODO  14814 10.65
			timeZero = (long)((14814*24+10.65)*60*60*1000);
		} else if (gameId.equals("GA523")) {
			// TODO  12.07
			timeZero = (long)((14814*24+12.07)*60*60*1000);
		} else if (gameId.equals("GA525")) {
			// TODO 13.64
			timeZero = (long)((14814*24+13.64)*60*60*1000);
		} else if (gameId.equals("GA526")) {
			// TODO 15.15
			timeZero = (long)((14814*24+15.15)*60*60*1000);
		} 
		else
			// day
			timeZero = (time/(24*60*60*1000))* (24*60*60*1000);
		
		return day+","+hour+","+((time-timeZero)/60000.0)+",";
	}
	static String getLoc(long time) {
		LatLng l1 = new LatLng(locLatitude, locLongitude);
		LatLng l2 = new LatLng(refLatitude, refLongitude);
		//LatLng l3 = new LatLng(lastLatitude, lastLongitude);
		// km to m
		String distanceMetres = locTime==0 ? "NA" : ""+(long)(l1.distance(l2)*1000);
		//String distanceMetresLast = lastLatitude==0 ? "NA" : ""+(long)(l1.distance(l3)*1000);
		
		return lastZone+","+locLongitude+","+locLatitude+","+(locTime==0 ? 0 : (time-locTime)/1000)+","+distanceMetres+","+0+",";
	}
	static class Msg {
		public long time;
		public int day;
		public double hour;
		public String locHeaders;
		public String playerId;
		public String gameId;
		public String messageId;
		public String type;
		public String year;
		public String title;
		public String description;
		public long prevTime;
		public boolean view = false;
		public long viewTime;
		public String action = "NA";
		public long actionTime;
	}
	static String getMsgLine(Msg msg, long postTime) {
		return getTime(msg.day,msg.hour,msg.time,msg.gameId)+msg.locHeaders+"message,"+msg.playerId+","+msg.gameId+","+msg.messageId+","+
		msg.type+","+msg.year+",\""+msg.title+"\",\""+msg.description+"\","+(msg.prevTime!=0 ? ""+(msg.time-msg.prevTime)/60000.0 : "NA")+","+
		(postTime!=0 ? ""+(postTime-msg.time)/60000.0 : "NA")+","+(msg.view? "Y":"N")+","+
		(msg.viewTime!=0 ? ""+(msg.viewTime-msg.time)/60000.0 : "NA")+","+msg.action+","+
		(msg.actionTime!=0 ? ""+(msg.actionTime-msg.time)/60000.0 : "NA");

	}
	static void parseFiles(){
		File rootdir = new File(directory);
		String dirs[] = rootdir.list();
		String line = null;
		
		PrintWriter loginPW = null;
		PrintWriter gamePW = null;
		PrintWriter messagePW = null;
		PrintWriter actionPW = null;
		try {
			loginPW = new PrintWriter(new FileWriter("login.csv"));
			loginPW.println("date,unixtime,event,playerName,clientId,conversationId,status,gameStatus,gameId");
			gamePW = new PrintWriter(new FileWriter("game.csv"));
			gamePW.println("day,hour,lon,lat,accuracy,age,event,clientId,playerId,gameId,year");
			messagePW = new PrintWriter(new FileWriter("message.csv"));
			messagePW.println("day,hour,"+getLocHeaders()+"event,playerId,gameId,messageId,type,year,title,description,preMins,postMins,view,viewMins,action,actionMins");
			actionPW = new PrintWriter(new FileWriter("action.csv"));
			actionPW.println("day,hour,"+getLocHeaders()+"action,playerId,gameId,year");
		}
		catch (Exception e) {
			System.err.println("Error: "+e);
			return;
		}

		Map<String,Integer> eventCount = new HashMap<String,Integer>();
		
		for (int i = 0; i < dirs.length; i++){

			String topdir = directory + separator + dirs[i];

			
			String[] files = new File(topdir).list();

			for (int j = 0; j < files.length; j++){

				BufferedReader in = null;

				try{
					System.out.println("parsing " + topdir+ separator + files[j]);
					in = new BufferedReader(new FileReader(topdir+ separator + files[j]));
				}
				catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					continue;
				}

				String json = null;
				String clientId = "";
				String playerId = "";
				String gameId = "";
				String lastYear = "";

				long timeZero = 0;
				int lineNumber = 0;
				
				String startAction = null;
				int startDay = 0;
				float startHour = 0;
				long startT =0;

				locTime = 0;
				locLatitude = 0;
				locLongitude = 0;
				locAccuracy = 0;
				
				lastZone = "";
				// immediately prior
				String prevZone = "";
				long lastZoneTime = 0;
				long minZoneTime = 10000; // 10s?!
				
				Msg lastMsg = null;
				
				while(true) {
					lineNumber++;
					try{
						line = in.readLine();
						if (line==null)
							break;
						
						int ix = line.indexOf(':');
						if (ix<0) {
							System.err.println(topdir+separator+files[j]+":"+lineNumber+": Discard line (missing ':'): "+line);
							continue;
						}

						String date =	null;
						long time = 0;
						int day = 0;
						float hour = 0;
						try {
							time = Long.parseLong(line.substring(0, ix));
							Calendar cal = Calendar.getInstance();
							cal.setTimeInMillis(time);
							day = (int)(time/(24*60*60*1000));
							hour = ((time-(24*60*60*1000L)*day)/(60*60*1000f));
							date =	DATE_FORMAT.format(	new Date(time));			
						} catch (Exception e) {
							System.err.println(topdir+separator+files[j]+":"+lineNumber+": Discard line (date): "+line);
							continue;
						}
						
						int ix2 = line.indexOf(':', ix+1);
						if (ix2<0) {
							System.err.println(topdir+separator+files[j]+":"+lineNumber+": Discard line (missing second ':'): "+line);
							continue;							
						}
						String event = line.substring(ix+1, ix2);
						if (!eventCount.containsKey(event))
							eventCount.put(event, new Integer(0));
						eventCount.put(event,eventCount.get(event)+1);
						
						json =  line.substring(ix2 + 1, line.length());

						if (event.equals("ClientState")){
							json = cleanClientState(json, in);
						}
						else if (event.equals("Client")){
							json = cleanClientJSON(json);
							JSONObject jo = parseIt(json);
							if (jo!=null && jo.has("msg")) {
								String message = jo.getString("msg");
								if (message.equals("sendQueueMessage.ok") && jo.has("messages")) {
									String messages = jo.getString("messages");
									JSONObject ro = parseMsg(messages);
									//System.out.println("Messages: "+ro);
									
									if (ro!=null && ro.has("array")) {
										JSONArray msgs = ro.getJSONArray("array");
										for (int mi=0; mi<msgs.length(); mi++) {
											JSONObject msg = msgs.getJSONObject(mi);
											if (msg.has("Message")) 
												msg = msg.getJSONObject("Message");
											if (msg.has("newVal")) {
												if (msg.get("newVal") instanceof JSONObject) {
													JSONObject val = msg.getJSONObject("newVal");
													//Iterator keys = val.keys();
													//while(keys.hasNext()) {
													//	String key = (String)keys.next();
													//	System.out.println("Found new "+key);
													//}
													if (val.has("Game")) {
														JSONObject game = val.getJSONObject("Game");
														// ID year state // System.out.println("");
														String gameId2 = (game.has("ID")) ? game.getString("ID"): "";
														if (!gameId2.equals(gameId))
															System.err.println("Warning: Game ID "+gameId2+" doesn't match Player gameId "+gameId);
														String year = (game.has("year")) ? game.getString("year"): "";
														String status = (game.has("status")) ? game.getString("status"): "";
														loginPW.println(date+",\""+time+"\",game,\""+clientId+"\",\""+gameId+"\",\""+year+"\",\""+status+"\"");
														if (year.equals("null"))
															year = "NA";
														gamePW.println(day+","+hour+","+locLongitude+","+locLatitude+","+locAccuracy+","+(locTime==0 ? 0 : (time-locTime)/1000)+",gamep,\""+clientId+"\",\""+playerId+"\",\""+gameId+"\","+year);
																//loginPW.println(date+",\""+time+"\",\""+cliloginattempt,\""+playerName+"\",\""+clientId2+"\",\""+conversationId+"\"");
													}
													else if (val.has("Player")) {
														JSONObject game = val.getJSONObject("Player");
														// ID year state // System.out.println("");
														String playerId2 = (game.has("ID")) ? game.getString("ID"): "";
														if (!playerId.equals(playerId2)) {
															playerId = playerId2;
															if (!playerId.equals(lastPlayer)) {
																lastPlayer = playerId;
																lastTime = 0;
																lastZone = "";
																lastLatitude = locLatitude;
																lastLongitude = locLongitude;
															}
														}
														String name = (game.has("name")) ? game.getString("name"): "";
														String gameId2 = (game.has("gameID")) ? game.getString("gameID"): "";
														if (!gameId.equals(gameId2)) {
															gameId = gameId2;
														}
														String canAuthor = (game.has("canAuthor")) ? game.getString("canAuthor"): "";
														String newMemberQuota = (game.has("newMemberQuota")) ? game.getString("newMemberQuota"): "";
														String points = (game.has("points")) ? game.getString("points"): "";
														loginPW.println(date+",\""+time+","+getLoc(time)+"player,\""+clientId+"\",\""+gameId+"\",\""+playerId+"\",\""+name+"\",\""+canAuthor+"\",\""+newMemberQuota+"\",\""+points+"\"");
														//gamePW.println("date,unixtime,clientId,event,gameId,year,status,playerId,canAuthor,newMemberQuota,points");
														//gamePW.println(date+",\""+time+"\",\""+clientId+"\",player,\""+gameId+"\",,,\""+playerId+"\",\""+name+"\",\""+canAuthor+"\",\""+newMemberQuota+"\",\""+points+"\"");

													}
												}
												//else // can be null 
												//	System.err.println("Found newVal "+msg.get("newVal"));
											}
										}
									}
								}
							}
						}
						else if (event.equals("LOCATION")){
							JSONObject jo = parseIt(json);
							//1279711577187:LOCATION:{"accuracy":3,"altitude":82,"bearing":132.890625,"latitude":52.951494455337524,"longitude":-1.1827093362808228,"provider":"gps","speed":0.75,"extras":"Bundle[mParcelledData.dataSize=4]"}
							locTime = time;
							locAccuracy = jo.has("accuracy") ? jo.getDouble("accuracy") : 0;
							locLatitude = jo.has("latitude") ? jo.getDouble("latitude") : 0;
							locLongitude = jo.has("longitude") ? jo.getDouble("longitude") : 0;
							LatLng l1 = new LatLng(locLatitude, locLongitude);
							LatLng l2 = new LatLng(refLatitude, refLongitude);
							// km to m
							String distanceMetres = locTime==0 ? "NA" : ""+(long)(l1.distance(l2)*1000);
							String movedString = "NA";
							if (lastTime!=0) {
								LatLng l3 = new LatLng(lastLatitude, lastLongitude);
								long distanceMoved = (long)(l1.distance(l3)*1000);
								if (distanceMoved<5 || time-lastTime < 15*000) {
									// ignore for now
								}
								else {
									// more than 3m/s average - glitch?!
									String action = (distanceMoved > (time-lastTime)*(3000/1000)) ? "jump" : "move";
									// move
									String loc = lastZone+","+locLongitude+","+locLatitude+","+(locTime==0 ? 0 : (time-locTime)/1000)+","+distanceMetres+","+distanceMoved+",";
									actionPW.println(getTime(day,hour,time,gameId)+loc+"\""+action+"\",\""+playerId+"\",\""+gameId+"\",NA");
									lastLatitude = locLatitude;
									lastLongitude = locLongitude;
									lastTime = time;
								}
							}
							else {
								// first move
								actionPW.println(getTime(day,hour,time,gameId)+getLoc(time)+"\"move\",\""+playerId+"\",\""+gameId+"\",NA");									
								lastLatitude = locLatitude;
								lastLongitude = locLongitude;
								lastTime = time;								
							}
						}
						else if (event.equals("GameState")){
							JSONObject jo = parseIt(json);
							if (jo!=null && jo.has("action")) {
								String action = jo.getString("action");
								if (action.equals("newMessage") && jo.has("message")) {
									JSONObject msg = parseMsg(jo.getString("message"));
									if (msg!=null && msg.has("Message")) 
										msg = msg.getJSONObject("Message");
									String id = msg!=null && msg.has("ID") ? msg.getString("ID") : "";
									String type = msg!=null && msg.has("type") ? msg.getString("type") : "";
									String year = msg!=null && msg.has("year") ? msg.getString("year") : "";
									String title = msg!=null && msg.has("title") ? msg.getString("title") : "";
									String description = msg!=null && msg.has("description") ? msg.getString("description") : "";
									//messagePW.println(getTime(day,hour,time,gameId)+getLoc(time)+"\"newMessage\",\""+playerId+"\",\""+gameId+"\",\""+id+"\",\""+type+"\","+year+",\""+title+"\"");
									//			messagePW.println("day,hour,"+getLocHeaders()+"event,playerId,gameId,messageId,type,year,title,preMins,postMins,view,viewMins,action,actionMins");

									long prevTime = (lastMsg!=null ? lastMsg.time : 0);
									if (lastMsg!=null) 
										messagePW.println(getMsgLine(lastMsg, time));
									lastMsg = new Msg();
									lastMsg.day = day;
									lastMsg.hour = hour;
									lastMsg.time = time;
									lastMsg.messageId = id;
									lastMsg.playerId = playerId;
									lastMsg.gameId = gameId;
									lastMsg.type = type;
									lastMsg.year = year;
									lastMsg.title = title;
									lastMsg.description = description;
									lastMsg.locHeaders = getLoc(time);
									lastMsg.prevTime = prevTime;
									
									actionPW.println(getTime(day,hour,time,gameId)+getLoc(time)+"\"newMessage\",\""+playerId+"\",\""+gameId+"\",NA");
								} else if(action.equals("updateZone") && jo.has("zoneID")) {
									String zone = jo.getString("zoneID");
									if (!zone.equals(lastZone) && time >= lastZoneTime+minZoneTime) {
										// change
										lastZone = zone;
										actionPW.println(getTime(day,hour,time,gameId)+getLoc(time)+"\"changeZone\",\""+playerId+"\",\""+gameId+"\",NA");
									}
									else if (zone.equals(lastZone) && !zone.equals(prevZone) && time<lastZoneTime+minZoneTime) {
										// changed back quickly - keep waiting
										lastZoneTime = time;
									}
									prevZone = zone;
								} else if(action.equals("updateYear") && jo.has("year")) {
									// 1279974791773:GameState:{"action":"updateYear","year":"1958"}
									String year = jo.getString("year");
									if (!year.equals(lastYear)) {
										// change
										lastYear = year;
										actionPW.println(getTime(day,hour,time,gameId)+getLoc(time)+"\"changeYear\",\""+playerId+"\",\""+gameId+"\",\""+year+"\"");
									}
								}

							}
						}
						else if (event.equals("BackgroundThread")){
							JSONObject jo = parseIt(json);
							if (jo!=null) {
								if (jo.has("message")) {
									String message = jo.getString("message");
									if ("login".equals(message)) {
										// 1279727493963:BackgroundThread:{"thread":"Thread-7","message":"login","request":"<login>\n  <clientId>354957031707824<\/clientId>\n  <clientType>AndroidDevclient<\/clientType>\n  <playerName>tom<\/playerName>\n  <conversationId>d8f9c66e186cbaa9865a<\/conversationId>\n  <clientVersion>1<\/clientVersion>\n<\/login>"}
										String request = jo.getString("request");
										Matcher m = Pattern.compile("<clientId>([^<]*)</clientId>").matcher(request);
										String clientId2 = "";
										if (m.find())
											clientId2 = m.group(1);
										m = Pattern.compile("<conversationId>([^<]*)</conversationId>").matcher(request);
										String conversationId = "";
										if (m.find())
											conversationId = m.group(1);
										m = Pattern.compile("<playerName>([^<]*)</playerName>").matcher(request);
										String playerName = "";
										if (m.find())
											playerName = m.group(1);
										if (!clientId.equals(clientId2)) {
											System.err.println("Warning: clientId "+clientId+" -> "+clientId2);
											clientId = clientId2;
											loginPW.println(date+",\""+time+"\",changeclientid,,\""+clientId2+"\",,,,\""+clientId+"\"");
										}
										loginPW.println(date+",\""+time+"\",loginattempt,\""+playerName+"\",\""+clientId2+"\",\""+conversationId+"\"");
									}
									else if ("loginReply".equals(message)) {
										//1279727498592:BackgroundThread:{"thread":"Thread-7","message":"loginReply","reply":"LoginReplyMessage [detail=null, gameId=GA505, gameStatus=NOT_STARTED, message=Welcome, status=OK]"}
										String reply = jo.getString("reply");
										JSONObject ro = parseMsg(reply);
										String status = ro!=null && ro.has("status") ? ro.getString("status") : "";
										String gameStatus = ro!=null && ro.has("gameStatus") ? ro.getString("gameStatus") : "";
										String gameId2 = ro!=null && ro.has("gameId") ? ro.getString("gameId") : "";
										loginPW.println(date+",\""+time+"\",loginreply,,,,\""+status+"\",\""+gameStatus+"\",\""+gameId2+"\"");
									}
								}
							}
						}
						else if (event.equals("GameAction")){
							JSONObject jo = parseIt(json);
							//actionPW.println("day,hour,action,playerId,gameId");
							if (jo!=null && jo.has("action")) {
								String action = jo.getString("action");
								if (action.endsWith(".start")) {
									if (lastMsg!=null && lastMsg.actionTime==0) {
										lastMsg.action = action;
										lastMsg.actionTime = time;
									}
									if (startAction!=null) {
										// flush
										actionPW.println(getTime(startDay,startHour,startT,gameId)+getLoc(time)+"\""+startAction+".started\",\""+playerId+"\",\""+gameId+"\",NA");
										startAction = null;
									}
									startAction = action.substring(0,action.length()-6);
									startHour = hour;
									startDay = day;
									startT = time;
								}
								else if (action.endsWith(".ok")) {
									if (startAction!=null) {
										// flush
										actionPW.println(getTime(startDay,startHour,startT,gameId)+getLoc(time)+"\""+startAction+".done\",\""+playerId+"\",\""+gameId+"\",NA");
										startAction = null;
									}									
								}
								else if (action.endsWith(".error")) {
									if (startAction!=null) {
										// flush
										actionPW.println(getTime(startDay,startHour,startT,gameId)+getLoc(time)+"\""+startAction+".failed\",\""+playerId+"\",\""+gameId+"\",NA");
										startAction = null;
									}									
								}
								else {
									if (lastMsg!=null && lastMsg.actionTime==0) {
										lastMsg.action = action;
										lastMsg.actionTime = time;
									}
									actionPW.println(getTime(day,hour,time,gameId)+getLoc(time)+"\""+action+"\",\""+playerId+"\",\""+gameId+"\",NA");
								}
							}
						}
						else if (event.equals("Activity")){
							JSONObject jo = parseIt(json);
							// viewing a message is missing from the GameEvent log...
							// 1279973223489:Activity:{"method":"onCreate","class":"com.littlebighead.exploding.TimeEventDialog","hashCode":1152522448}
							if (jo!=null && jo.has("class") && jo.has("method")) {
								if ("onCreate".equals(jo.getString("method")) && "com.littlebighead.exploding.TimeEventDialog".equals(jo.getString("class"))) {
									String action = "ViewMessage";
									actionPW.println(getTime(day,hour,time,gameId)+getLoc(time)+"\""+action+"\",\""+playerId+"\",\""+gameId+"\",NA");
									if (lastMsg!=null && !lastMsg.view) {
										lastMsg.view = true;
										lastMsg.viewTime = time;
									}
								}
							}
						}
						else if (event.equals("LogHeader-v1")){
							JSONObject jo = parseIt(json);
							if (jo.has("imei"))
								clientId = jo.getString("imei");
						}
						else if (event.equals("Application")){
							parseIt(json);
						}
						else if (event.equals("UncaughtException")){
							parseIt(json);
							System.err.println("Note: "+line);
						}
						else {
							System.err.println("Unhandled "+event+": "+line);
							parseIt(json);							
						}
					}catch(Exception e){
						System.err.println(topdir+separator+files[j]+":"+lineNumber+": "+e);
						System.err.println("Line: "+line);
						e.printStackTrace(System.err);
					}
				}
				if (lastMsg!=null) 
					messagePW.println(getMsgLine(lastMsg, 0));

			}
		}
		loginPW.close();
		gamePW.close();
		messagePW.close();
		actionPW.close();
		System.out.println("Events:");
		for (String event : eventCount.keySet()) {
			System.out.println(event+":\t"+eventCount.get(event));
		}
	}

	private static JSONObject parseMsg(String reply) {
		// try to convert/parse the dump format as JSON
		// e.g. LoginReplyMessage [detail=null, gameId=GA505, gameStatus=NOT_STARTED, message=Welcome, status=OK]
		StringBuilder sb = new StringBuilder();
		boolean quoted = false;
		StringBuilder ws = new StringBuilder();
		StringBuilder word = new StringBuilder();
		boolean started = false;
		Vector<Boolean> addObject = new Vector<Boolean>();
		// first word?
		for (int i=0; i<reply.length(); i++) {
			char c = reply.charAt(i);
			if (Character.isWhitespace(c))
			{
				if (started) {
					if (quoted)
						ws.append(c);
					else
						sb.append(c);
				}
			}
			else if (c=='[' ||  c=='{') {
				boolean obj = quoted;
				addObject.add(quoted);
				if (quoted) {
					// note extra ':'
					//sb.append("\":"+ws);
					word.append("\""+ws);
					quoted = false;
					ws = new StringBuilder();
					sb.append("{"+word+":");
					word = new StringBuilder();
				}
				if (i+5<=reply.length() && reply.substring(i, i+5).equals("[Luk.")) {
					// array special case
					addObject.remove(addObject.size()-1);
					// skip
					continue;
				} 
				started = true;
				if (obj)
					sb.append("{");
				else
					sb.append("[");
			}
			else if (c==']' || c=='}') {
				if (quoted) {
					word.append("\""+ws);
					quoted = false;
					ws = new StringBuilder();
					sb.append(word);
					word = new StringBuilder();
				}
				if (addObject.size()==0) {
					System.err.println("Mismatched ]: "+reply);
				}
				else {
					boolean add = addObject.remove(addObject.size()-1);
					if (add)
						sb.append("}}");
					else 
						sb.append("]");
				}
			}
			else if (c=='=') {
				if (quoted) {
					word.append("\""+ws);
					quoted = false;
					ws = new StringBuilder();
					sb.append(word);
					word = new StringBuilder();
				}
				sb.append(":");
				//special case : empty string
				if (i+1<reply.length() && (reply.charAt(i+1)==',' || reply.charAt(i+1)==']' || reply.charAt(i+1)=='}'))
					sb.append("\"\"");
			}
			else if (c==':') {
				// discard?!
//				if (quoted) {
//					word.append("\""+ws);
//					quoted = false;
//					ws = new StringBuilder();
//					sb.append(word);
//					word = new StringBuilder();
//				}
//				sb.append(c);
			}
			else if (c==',') {
				
				if (quoted) {					
					// special case: limbData number list
					if (i+1<reply.length() && (reply.charAt(i+1)=='-' || Character.isDigit(reply.charAt(i+1)))) {
						// treat as normal char
						word.append(c);
						continue;
					}
					// , in text special case? look ahead to next ']', '}' or ',key='
					int ix1 = reply.indexOf(']', i);
					int ix2 = reply.indexOf('}', i);
					int ix3 = reply.indexOf('=', i);
					if (ix3>0 && (ix1<0 || ix1>ix3) && (ix2<0 || ix2>ix3)) {
						// = is first thing...
						// is next , after this before that =?
						int cix = reply.indexOf(',', i+1);
						if (cix>i && cix<ix3) {
							// we think this is an internal ,
							word.append(c);
							continue;
						}
					}
					word.append("\""+ws);
					quoted = false;
					ws = new StringBuilder();
					sb.append(word);
					word = new StringBuilder();
				}
				sb.append(',');
			} else if (c=='"') {
				// escape?
				// skip?
				continue;
			} else {
				if (!quoted) {
					word.append("\"");
					quoted = true;
				}
				word.append(ws);
				ws = new StringBuilder();
				word.append(c);
			}
		}
		if (quoted)
			sb.append("\"");
		
		String asjson = sb.toString();
		try {
			if (asjson.startsWith("["))
				asjson = "{\"array\":"+asjson+"}";
			return new JSONObject(asjson);
		}
		catch (Exception e) {
			System.err.println("Error mapping to json: from "+reply+" to "+asjson+": "+e);			
		}
		return null;
	}
}