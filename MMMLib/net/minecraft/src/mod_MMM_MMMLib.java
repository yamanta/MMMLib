package net.minecraft.src;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.client.Minecraft;

public class mod_MMM_MMMLib extends BaseMod {

	public static final int MMM_Server_GetTextureIndex	= 0x00;
	public static final int MMM_Server_GetTextureStr	= 0x01;
	public static final int MMM_Client_SetTextureIndex	= 0x00;
	public static final int MMM_Client_SetTextureStr	= 0x01;
	
	
	@MLProp()
	public static boolean isDebugView = false;
	@MLProp()
	public static boolean isDebugMessage = true;
	@MLProp(info = "Override RenderItem.")
	public static boolean renderHacking = true;
	
	
	public static void Debug(String pText) {
		// デバッグメッセージ
		if (isDebugMessage) {
			System.out.println(String.format("MMMLib-%s", pText));
		}
	}

	@Override
	public String getName() {
		return "MMMLib";
	}

	@Override
	public String getVersion() {
		return "1.4.7-1";
	}
	
	@Override
	public String getPriorities() {
		return MMM_Helper.isForge ? "befor-all" : "before:*";
	}

	@Override
	public void load() {
		// 初期化
		Debug(MMM_Helper.isClient ? "Client" : "Server");
		Debug(MMM_Helper.isForge ? "Forge" : "Modloader");
		MMM_FileManager.init();
		MMM_TextureManager.init();
		MMM_StabilizerManager.init();
		ModLoader.setInGameHook(this, true, true);
		if (isDebugView) {
			MMM_EntityDummy.isEnable = true;
		}
		
		// 独自パケット用チャンネル
		ModLoader.registerPacketChannel(this, "MMM|Upd");
	}

	@Override
	public void modsLoaded() {
		// ロード
		if (MMM_Helper.isClient) {
			// テクスチャパックの構築
			MMM_TextureManager.loadTextures();
			MMM_StabilizerManager.loadStabilizer();
		} else {
			MMM_TextureManager.loadTextureIndex();
		}
		
		// テクスチャインデックスの構築
		Debug("Localmode: InitTextureList.");
		MMM_TextureManager.initTextureList(true);
	}

	@Override
	public void addRenderer(Map var1) {
		if (isDebugView) {
			var1.put(net.minecraft.src.MMM_EntityDummy.class, new MMM_RenderDummy());
		}
		//RenderItem
		var1.put(EntityItem.class, new MMM_RenderItem());
	}

	@Override
	public boolean onTickInGame(float var1, Minecraft var2) {
		if (isDebugView && MMM_Helper.isClient) {
			// ダミーマーカーの表示用処理
			if (var2.theWorld != null && var2.thePlayer != null) {
				try {
					for (Iterator<MMM_EntityDummy> li = MMM_EntityDummy.appendList.iterator(); li.hasNext();) {
						var2.theWorld.spawnEntityInWorld(li.next());
						li.remove();
					}
				} catch (Exception e) {
//					e.printStackTrace();
				}
			}
		}
		
		// アイテムレンダーをオーバーライド
		if (renderHacking && MMM_Helper.isClient) {
			MMM_Client.setItemRenderer();
		}
		
		return true;
	}

	@Override
	public void clientCustomPayload(NetClientHandler var1, Packet250CustomPayload var2) {
		// クライアント側の特殊パケット受信動作
		byte lmode = var2.data[0];
		int leid = 0;
		Entity lentity = null;
		if ((lmode & 0x80) != 0) {
			leid = MMM_Helper.getInt(var2.data, 1);
			lentity = MMM_Helper.getEntity(var2.data, 1, MMM_Helper.mc.theWorld);
			if (lentity == null) return;
		}
		Debug(String.format("MMM|Upd Clt Call[%2x:%d].", lmode, leid));
		
		switch (lmode) {
		case MMM_Client_SetTextureIndex:
			// テクスチャ名称のリクエストに対して番号を返す
			/*
			 * 0:id
			 * 1:index
			 * 2-5:TextureIndex
			 */
			int li7 = MMM_Helper.getShort(var2.data, 2);
			String ls7 = MMM_TextureManager.getRequestString(var2.data[1]);
			Debug(String.format("%d = %d : %s", li7, var2.data[1], ls7 == null ? "NULL" : ls7));
			MMM_TextureManager.setStringToIndex(li7, ls7);
			break;
		case MMM_Client_SetTextureStr:
			// テクスチャ名称のリクエストに対して番号を返す
			/*
			 * 0:id
			 * 1-2:index 登録テクスチャ番号
			 * 3-:Str 名称
			 */
			int li8 = MMM_Helper.getShort(var2.data, 1);
			String ls8 = MMM_Helper.getStr(var2.data, 3);
			Debug(String.format("%d = %s", li8, ls8 == null ? "NULL" : ls8));
			MMM_TextureManager.setStringToIndex(li8, ls8);
			break;
			
		}
	}

	public static void sendToServer(byte[] pData) {
		ModLoader.clientSendPacket(new Packet250CustomPayload("MMM|Upd", pData));
		Debug(String.format("MMM|Upd:%2x:NOEntity", pData[0]));
	}

	@Override
	public void serverCustomPayload(NetServerHandler var1, Packet250CustomPayload var2) {
		// サーバ側の動作
		byte lmode = var2.data[0];
		int leid = 0;
		Entity lentity = null;
		if ((lmode & 0x80) != 0) {
			leid = MMM_Helper.getInt(var2.data, 1);
			lentity = MMM_Helper.getEntity(var2.data, 1, var1.playerEntity.worldObj);
			if (lentity == null) return;
		}
		Debug(String.format("MMM|Upd Srv Call[%2x:%d].", lmode, leid));
		byte[] ldata;
		
		switch (lmode) {
		case MMM_Server_GetTextureIndex:
			// テクスチャ名称のリクエストに対して番号を返す
			/*
			 * 0:ID
			 * 1:index 要求かけた時の番号
			 * 2-3:contColorBits
			 * 4-5:wildColorBits
			 * 6-9:height
			 * 10-13:width
			 * 14-17:yoffset
			 * 18-:Str
			 */
			MMM_TextureBoxServer lbox = new MMM_TextureBoxServer(var2.data);
			int li = MMM_TextureManager.setTextureBoxToIndex(lbox);
			Debug(String.format("%d = %d : %04x : %s", li, var2.data[1], lbox.wildColor, lbox.textureName == null ? "NULL" : lbox.textureName));
			ldata = new byte[] {
					MMM_Client_SetTextureIndex,
					var2.data[1],
					0, 0
			};
			MMM_Helper.setShort(ldata, 2, li);
			sendToClient(var1, ldata);
			break;
		case MMM_Server_GetTextureStr:
			// インデックスからテクスチャ名称を返す
			/*
			 * 0:ID
			 * 1-2:index 登録テクスチャ番号
			 */
			int li8 = MMM_Helper.getShort(var2.data, 1);
			MMM_TextureBoxServer lb8 = MMM_TextureManager.getIndexToString(li8);
			Debug(String.format("%d : %s", li8, lb8.textureName == null ? "NULL" : lb8.textureName));
			ldata = new byte[3 + lb8.textureName.getBytes().length];
			ldata[0] = MMM_Client_SetTextureStr;
			ldata[1] = var2.data[1];
			ldata[2] = var2.data[2];
			MMM_Helper.setStr(ldata, 3, lb8.textureName);
			sendToClient(var1, ldata);
			break;
			
		}
	}

	public static void sendToClient(NetServerHandler pHandler, byte[] pData) {
		ModLoader.serverSendPacket(pHandler, new Packet250CustomPayload("MMM|Upd", pData));
	}

	@Override
	public void clientConnect(NetClientHandler var1) {
		if (MMM_Helper.mc.isIntegratedServerRunning()) {
//			Debug("Localmode: InitTextureList.");
//			MMM_TextureManager.initTextureList(true);
		} else {
			Debug("Remortmode: ClearTextureList.");
			MMM_TextureManager.initTextureList(false);
		}
	}

	@Override
	public void clientDisconnect(NetClientHandler var1) {
//		super.clientDisconnect(var1);
		Debug("Localmode: InitTextureList.");
		MMM_TextureManager.initTextureList(true);
	}
	

}
