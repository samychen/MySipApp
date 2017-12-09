/* $Id: MyApp.java 5102 2015-05-28 07:14:24Z riza $ */
/*
 * Copyright (C) 2013 Teluu Inc. (http://www.teluu.com)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.haige.app;

import org.pjsip.pjsua2.Account;
import org.pjsip.pjsua2.AccountConfig;
import org.pjsip.pjsua2.AudioMedia;
import org.pjsip.pjsua2.Buddy;
import org.pjsip.pjsua2.BuddyConfig;
import org.pjsip.pjsua2.BuddyInfo;
import org.pjsip.pjsua2.Call;
import org.pjsip.pjsua2.CallInfo;
import org.pjsip.pjsua2.CallMediaInfo;
import org.pjsip.pjsua2.CallMediaInfoVector;
import org.pjsip.pjsua2.ContainerNode;
import org.pjsip.pjsua2.Endpoint;
import org.pjsip.pjsua2.EpConfig;
import org.pjsip.pjsua2.JsonDocument;
import org.pjsip.pjsua2.LogConfig;
import org.pjsip.pjsua2.LogEntry;
import org.pjsip.pjsua2.LogWriter;
import org.pjsip.pjsua2.Media;
import org.pjsip.pjsua2.OnCallMediaStateParam;
import org.pjsip.pjsua2.OnCallStateParam;
import org.pjsip.pjsua2.OnIncomingCallParam;
import org.pjsip.pjsua2.OnInstantMessageParam;
import org.pjsip.pjsua2.OnRegStateParam;
import org.pjsip.pjsua2.StringVector;
import org.pjsip.pjsua2.TransportConfig;
import org.pjsip.pjsua2.UaConfig;
import org.pjsip.pjsua2.pj_log_decoration;
import org.pjsip.pjsua2.pjmedia_type;
import org.pjsip.pjsua2.pjsip_evsub_state;
import org.pjsip.pjsua2.pjsip_inv_state;
import org.pjsip.pjsua2.pjsip_status_code;
import org.pjsip.pjsua2.pjsip_transport_type_e;
import org.pjsip.pjsua2.pjsua_buddy_status;
import org.pjsip.pjsua2.pjsua_call_media_status;

import java.io.File;
import java.util.ArrayList;


/* Interface to separate UI & engine a bit better */
interface MyAppObserver {
	abstract void notifyRegState(pjsip_status_code code, String reason, int expiration);
	abstract void notifyIncomingCall(MyCall call);
	abstract void notifyCallState(MyCall call);
	abstract void notifyBuddyState(MyBuddy buddy);
}


class MyLogWriter extends LogWriter {
	@Override
	public void write(LogEntry entry) {
		System.out.println(entry.getMsg());
	}
}

/**
 * 在PJSIP中，涉及发送和接收SIP消息的所有操作都是异步的，这意味着调用该操作的功能将立即完成，您将在回调中获得完成状态。\
 * 例如Call类的makeCall( ) 方法。此功能用于启动到目的地的呼出。当此函数成功返回时，并不意味着该呼叫已经建立，而是意味着该呼叫已成功启动。
 * 您将在Call类的onCallState（）回调方法中获取呼叫进度和/或完成的报告。
 */
class MyCall extends Call {
	MyCall(MyAccount acc, int call_id) {
		super(acc, call_id);
	}

	@Override
	public void onCallState(OnCallStateParam prm) {
		//pjsip call类回调通话状态
		MyApp.observer.notifyCallState(this);
		try {
			CallInfo ci = getInfo();
			if (ci.getState() == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
				this.delete();
			}
		} catch (Exception e) {
			return;
		}
	}
	
	@Override
	public void onCallMediaState(OnCallMediaStateParam prm) {
		CallInfo ci;
		try {
			ci = getInfo();
		} catch (Exception e) {
			return;
		}
		CallMediaInfoVector cmiv = ci.getMedia();
		for (int i = 0; i < cmiv.size(); i++) {
			CallMediaInfo cmi = cmiv.get(i);
			if (cmi.getType() == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
			    (cmi.getStatus() == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE ||
			     cmi.getStatus() == pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD))
			{
				// unfortunately, on Java too, the returned Media cannot be downcasted to AudioMedia 
				Media m = getMedia(i);
				AudioMedia am = AudioMedia.typecastFromMedia(m);
				
				// connect ports
				try {
					MyApp.ep.audDevManager().getCaptureDevMedia().startTransmit(am);
					am.startTransmit(MyApp.ep.audDevManager().getPlaybackDevMedia());
				} catch (Exception e) {
					continue;
				}
			}
		}
	}
}

/**
 * 参考链接{@http://www.cnblogs.com/mobilecard/p/6708651.html}{@http://www.cnblogs.com/mobilecard/p/6723742.html}
 */
class MyAccount extends Account {
	public ArrayList<MyBuddy> buddyList = new ArrayList<MyBuddy>();//好友列表
	public AccountConfig cfg;//账户配置
	
	MyAccount(AccountConfig config) {
		super();
		cfg = config;
	}
	
	public MyBuddy addBuddy(BuddyConfig bud_cfg)
	{
		/* Create Buddy */
		MyBuddy bud = new MyBuddy(bud_cfg);
		try {
			bud.create(this, bud_cfg);
		} catch (Exception e) {
			bud.delete();
			bud = null;
		}
		
		if (bud != null) {
			buddyList.add(bud);
			if (bud_cfg.getSubscribe())
				try {
					bud.subscribePresence(true);
				} catch (Exception e) {}
		}
		
		return bud;
	}
	
	public void delBuddy(MyBuddy buddy) {
		buddyList.remove(buddy);
		buddy.delete();
	}
	
	public void delBuddy(int index) {
		MyBuddy bud = buddyList.get(index);
		buddyList.remove(index);
		bud.delete();
	}
	
	@Override
	public void onRegState(OnRegStateParam prm) {
		//回调账户注册结果，返回返回码，返回原因和过期时间
		MyApp.observer.notifyRegState(prm.getCode(), prm.getReason(), prm.getExpiration());
	}

	@Override
	public void onIncomingCall(OnIncomingCallParam prm) {
		System.out.println("======== Incoming call ======== ");
		MyCall call = new MyCall(this, prm.getCallId());
		MyApp.observer.notifyIncomingCall(call);
	}

	/**
	 * 不是从伙伴传入的及时消息打印sip协议栈日志
	 * @param prm
	 */
	@Override
	public void onInstantMessage(OnInstantMessageParam prm) {
		System.out.println("======== Incoming pager ======== ");
		System.out.println("From 		: " + prm.getFromUri());
		System.out.println("To			: " + prm.getToUri());
		System.out.println("Contact		: " + prm.getContactUri());
		System.out.println("Mimetype	: " + prm.getContentType());
		System.out.println("Body		: " + prm.getMsgBody());
	}
}

/**
 * 好友
 */
class MyBuddy extends Buddy {
	public BuddyConfig cfg;
	
	MyBuddy(BuddyConfig config) {
		super();
		cfg = config;
	}
	
	String getStatusText() {
		BuddyInfo bi;
		try {
			bi = getInfo();
		} catch (Exception e) {
			return "?";
		}
		String status = "";
		if (bi.getSubState() == pjsip_evsub_state.PJSIP_EVSUB_STATE_ACTIVE) {//订阅成功，活跃状态
			if (bi.getPresStatus().getStatus() == pjsua_buddy_status.PJSUA_BUDDY_STATUS_ONLINE) {
				status = bi.getPresStatus().getStatusText();
				if (status == null || status.length()==0) {
					status = "Online";
				}
			} else if (bi.getPresStatus().getStatus() == pjsua_buddy_status.PJSUA_BUDDY_STATUS_OFFLINE) {
				status = "Offline";
			} else {
				status = "Unknown";
			}
		}
		return status;
	}

	@Override
	public void onBuddyState() {
		//回调好友的状态并通知好友状态变更
		MyApp.observer.notifyBuddyState(this);
	}
	
}


class MyAccountConfig {
	public AccountConfig accCfg = new AccountConfig();
	public ArrayList<BuddyConfig> buddyCfgs = new ArrayList<BuddyConfig>();
	
	public void readObject(ContainerNode node) {
		try {
			ContainerNode acc_node = node.readContainer("Account");
			accCfg.readObject(acc_node);
			ContainerNode buddies_node = acc_node.readArray("buddies");
			buddyCfgs.clear();
			while (buddies_node.hasUnread()) {
				BuddyConfig bud_cfg = new BuddyConfig(); 
				bud_cfg.readObject(buddies_node);
				buddyCfgs.add(bud_cfg);
			}
		} catch (Exception e) {}
	}
	
	public void writeObject(ContainerNode node) {
		try {
			ContainerNode acc_node = node.writeNewContainer("Account");
			accCfg.writeObject(acc_node);
			ContainerNode buddies_node = acc_node.writeNewArray("buddies");
			for (int j = 0; j < buddyCfgs.size(); j++) {
				buddyCfgs.get(j).writeObject(buddies_node);
			}
		} catch (Exception e) {}
	}
}


class MyApp {
	static {
		System.loadLibrary("pjsua2");
		System.out.println("Library loaded");
	}
	//Endpoint类是一个单例类，应用程序必须在此类实例之前创建一个并且最多只能创建一个，然后才能执行任何操作。{@http://www.cnblogs.com/mobilecard/p/6713373.html}
	public static Endpoint ep = new Endpoint();
	public static MyAppObserver observer;
	public ArrayList<MyAccount> accList = new ArrayList<MyAccount>();

	private ArrayList<MyAccountConfig> accCfgs = new ArrayList<MyAccountConfig>();
	private EpConfig epConfig = new EpConfig();//端点配置
	private TransportConfig sipTpConfig = new TransportConfig();
	private String appDir;
	
	/* Maintain reference to log writer to avoid premature cleanup by GC */
	private MyLogWriter logWriter;

	private final String configName = "pjsua2.json";
	private final int SIP_PORT  = 6000;
	private final int LOG_LEVEL = 4;
	
	public void init(MyAppObserver obs, String app_dir) {
		init(obs, app_dir, false);
	}
	
	public void init(MyAppObserver obs, String app_dir, boolean own_worker_thread) {
		observer = obs;
		appDir = app_dir;
		/* Create endpoint */
		try {
			//实例化pjsua应用程序。调用任何其他函数之前，应用程序必须调用此函数，以确保底层库被正确初始化。一旦此函数返回成功，应用程序必须在退出前调用libDestroy（）
			ep.libCreate();
		} catch (Exception e) {
			return;
		}
		/* Load config */
		String configPath = appDir + "/" + configName;
		File f = new File(configPath);
		if (f.exists()) {
			loadConfig(configPath);
		} else {
			/* Set 'default' values */
			sipTpConfig.setPort(SIP_PORT);
		}
		/* Override log level setting */
		epConfig.getLogConfig().setLevel(LOG_LEVEL);
		epConfig.getLogConfig().setConsoleLevel(LOG_LEVEL);
		
		/* Set log config. */
		LogConfig log_cfg = epConfig.getLogConfig();
		logWriter = new MyLogWriter();
		log_cfg.setWriter(logWriter);
		log_cfg.setDecor(log_cfg.getDecor() & 
						 ~(pj_log_decoration.PJ_LOG_HAS_CR.swigValue() | 
						   pj_log_decoration.PJ_LOG_HAS_NEWLINE.swigValue()));
		
		/* Set ua config. */
		UaConfig ua_cfg = epConfig.getUaConfig();//用户代理配置从端点配置中获得
		ua_cfg.setUserAgent("Pjsua2 Android " + ep.libVersion().getFull());
		StringVector stun_servers = new StringVector();
		stun_servers.add("stun.pjsip.org");
		ua_cfg.setStunServer(stun_servers);
		if (own_worker_thread) {
			ua_cfg.setThreadCnt(0);
			ua_cfg.setMainThreadOnly(true);
		}
		
		/* Init endpoint */
		try {
			ep.libInit(epConfig);
		} catch (Exception e) {
			return;
		}
		
		/* Create transports. */
		//指定UDP传输
		try {
			ep.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, sipTpConfig);
		} catch (Exception e) {
			System.out.println(e);
		}
		//指定TCP传输
		try {
			ep.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP, sipTpConfig);
		} catch (Exception e) {
			System.out.println(e);
		}
		
		/* Create accounts. */
		for (int i = 0; i < accCfgs.size(); i++) {
			MyAccountConfig my_cfg = accCfgs.get(i);
			MyAccount acc = addAcc(my_cfg.accCfg);
			if (acc == null)
				continue;
			
			/* Add Buddies */
			for (int j = 0; j < my_cfg.buddyCfgs.size(); j++) {
				BuddyConfig bud_cfg = my_cfg.buddyCfgs.get(j);
				acc.addBuddy(bud_cfg);
			}
		}

		/* Start. */
		try {
			ep.libStart();
		} catch (Exception e) {
			return;
		}
	}
	
	public MyAccount addAcc(AccountConfig cfg) {
		MyAccount acc = new MyAccount(cfg);
		try {
			acc.create(cfg);
		} catch (Exception e) {
			acc = null;
			return null;
		}
		
		accList.add(acc);
		return acc;
	}
	
	public void delAcc(MyAccount acc) {
		accList.remove(acc);
	}
	
	private void loadConfig(String filename) {
		JsonDocument json = new JsonDocument();
		
		try {
			/* Load file */
			json.loadFile(filename);
			ContainerNode root = json.getRootContainer();
			
			/* Read endpoint config */
			epConfig.readObject(root);
			
			/* Read transport config */
			ContainerNode tp_node = root.readContainer("SipTransport");
			sipTpConfig.readObject(tp_node);
			
			/* Read account configs */
			accCfgs.clear();
			ContainerNode accs_node = root.readArray("accounts");
			while (accs_node.hasUnread()) {
				MyAccountConfig acc_cfg = new MyAccountConfig();
				acc_cfg.readObject(accs_node);
				accCfgs.add(acc_cfg);
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		
		/* Force delete json now, as I found that Java somehow destroys it
		 * after lib has been destroyed and from non-registered thread.
		 */
		json.delete();
	}

	private void buildAccConfigs() {
		/* Sync accCfgs from accList */
		accCfgs.clear();
		for (int i = 0; i < accList.size(); i++) {
			MyAccount acc = accList.get(i);
			MyAccountConfig my_acc_cfg = new MyAccountConfig();
			my_acc_cfg.accCfg = acc.cfg;
			
			my_acc_cfg.buddyCfgs.clear();
			for (int j = 0; j < acc.buddyList.size(); j++) {
				MyBuddy bud = acc.buddyList.get(j);
				my_acc_cfg.buddyCfgs.add(bud.cfg);
			}
			
			accCfgs.add(my_acc_cfg);
		}
	}
	
	private void saveConfig(String filename) {
		JsonDocument json = new JsonDocument();
		try {
			/* Write endpoint config */
			json.writeObject(epConfig);
			
			/* Write transport config */
			ContainerNode tp_node = json.writeNewContainer("SipTransport");
			sipTpConfig.writeObject(tp_node);
			
			/* Write account configs */
			buildAccConfigs();
			ContainerNode accs_node = json.writeNewArray("accounts");
			for (int i = 0; i < accCfgs.size(); i++) {
				accCfgs.get(i).writeObject(accs_node);
			}
			
			/* Save file */
			json.saveFile(filename);
		} catch (Exception e) {}

		/* Force delete json now, as I found that Java somehow destroys it
		 * after lib has been destroyed and from non-registered thread.
		 */
		json.delete();
	}
	
	public void deinit() {
		String configPath = appDir + "/" + configName;
		saveConfig(configPath);
		
		/* Try force GC to avoid late destroy of PJ objects as they should be
		 * deleted before lib is destroyed.
		 */
		Runtime.getRuntime().gc();
		
		/* Shutdown pjsua. Note that Endpoint destructor will also invoke
		 * libDestroy(), so this will be a test of double libDestroy().
		 */
		try {
			ep.libDestroy();
		} catch (Exception e) {}
		
		/* Force delete Endpoint here, to avoid deletion from a non-
		 * registered thread (by GC?). 
		 */
		ep.delete();
		ep = null;
	} 
}
