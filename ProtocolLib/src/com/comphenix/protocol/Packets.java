package com.comphenix.protocol;

import com.comphenix.protocol.reflect.IntEnum;

/**
 * List of known packet IDs since 1.3.2.
 * 
 * @author Kristian
 */
public final class Packets {
	
	/**
	 * List of packets sent only by the server.
	 * @author Kristian
	 */
	public static final class Server extends IntEnum {
		/**
		 * The singleton instance. Can also be retrieved from the parent class.
		 */
		private static Server INSTANCE = new Server();
		
		public static final int KEEP_ALIVE = 0;
		public static final int LOGIN = 1;
		public static final int CHAT = 3;
		public static final int UPDATE_TIME = 4;
		public static final int ENTITY_EQUIPMENT = 5;
		public static final int SPAWN_POSITION = 6;
		public static final int UPDATE_HEALTH = 8;
		public static final int RESPAWN = 9;
		public static final int FLYING = 10;
		public static final int PLAYER_POSITION = 11;
		public static final int PLAYER_LOOK = 12;
		public static final int PLAYER_LOOK_MOVE = 13;
		public static final int ENTITY_LOCATION_ACTION = 17;
		public static final int ARM_ANIMATION = 18;
		public static final int NAMED_ENTITY_SPAWN = 20;
		public static final int PICKUP_SPAWN = 21;
		public static final int COLLECT = 22;
		public static final int VEHICLE_SPAWN = 23;
		public static final int MOB_SPAWN = 24;
		public static final int ENTITY_PAINTING = 25;
		public static final int ADD_EXP_ORB = 26;
		public static final int ENTITY_VELOCITY = 28;
		public static final int DESTROY_ENTITY = 29;
		public static final int ENTITY = 30;
		public static final int REL_ENTITY_MOVE = 31;
		public static final int ENTITY_LOOK = 32;
		public static final int REL_ENTITY_MOVE_LOOK = 33;
		public static final int ENTITY_TELEPORT = 34;
		public static final int ENTITY_HEAD_ROTATION = 35;
		public static final int ENTITY_STATUS = 38;
		public static final int ATTACH_ENTITY = 39;
		public static final int ENTITY_METADATA = 40;
		public static final int MOB_EFFECT = 41;
		public static final int REMOVE_MOB_EFFECT = 42;
		public static final int SET_EXPERIENCE = 43;
		public static final int MAP_CHUNK = 51;
		public static final int MULTI_BLOCK_CHANGE = 52;
		public static final int BLOCK_CHANGE = 53;
		public static final int PLAY_NOTE_BLOCK = 54;
		public static final int BLOCK_BREAK_ANIMATION = 55;
		public static final int MAP_CHUNK_BULK = 56;
		public static final int EXPLOSION = 60;
		public static final int WORLD_EVENT = 61;
		public static final int NAMED_SOUND_EFFECT = 62;
		public static final int BED = 70;
		public static final int WEATHER = 71;
		public static final int OPEN_WINDOW = 100;
		public static final int CLOSE_WINDOW = 101;
		public static final int SET_SLOT = 103;
		public static final int WINDOW_ITEMS = 104;
		public static final int CRAFT_PROGRESS_BAR = 105;
		public static final int TRANSACTION = 106;
		public static final int SET_CREATIVE_SLOT = 107;
		public static final int UPDATE_SIGN = 130;
		public static final int ITEM_DATA = 131;
		public static final int TILE_ENTITY_DATA = 132;
		public static final int STATISTIC = 200;
		public static final int PLAYER_INFO = 201;
		public static final int ABILITIES = 202;
		public static final int TAB_COMPLETE = 203;
		public static final int CUSTOM_PAYLOAD = 250;
		public static final int KEY_RESPONSE = 252;
		public static final int KEY_REQUEST = 253;
		public static final int KICK_DISCONNECT = 255;
		
		/**
		 * A registry that parses between names and packet IDs.
		 * @return The current server registry.
		 */
		public static Server getRegistry() {
			return INSTANCE;
		}
		
		// We only allow a single instance of this class
	    private Server() {
			super();
		}
	}
	
	/**
	 * List of packets sent by the client.
	 * @author Kristian
	 */
	public static class Client extends IntEnum {
		/**
		 * The singleton instance. Can also be retrieved from the parent class.
		 */
		private static Client INSTANCE = new Client();
		
		public static final int KEEP_ALIVE = 0;
		public static final int LOGIN = 1;
		public static final int HANDSHAKE = 2;
		public static final int CHAT = 3;
		public static final int USE_ENTITY = 7;
		public static final int RESPAWN = 9;
		public static final int FLYING = 10;
		public static final int PLAYER_POSITION = 11;
		public static final int PLAYER_LOOK = 12;
		public static final int PLAYER_LOOK_MOVE = 13;
		public static final int BLOCK_DIG = 14;
		public static final int PLACE = 15;
		public static final int BLOCK_ITEM_SWITCH = 16;
		public static final int ARM_ANIMATION = 18;
		public static final int ENTITY_ACTION = 19;
		public static final int CLOSE_WINDOW = 101;
		public static final int WINDOW_CLICK = 102;
		public static final int TRANSACTION = 106;
		public static final int SET_CREATIVE_SLOT = 107;
		public static final int BUTTON_CLICK = 108;
		public static final int UPDATE_SIGN = 130;
		public static final int ABILITIES = 202;
		public static final int TAB_COMPLETE = 203;
		public static final int LOCALE_AND_VIEW_DISTANCE = 204;
		public static final int CLIENT_COMMAND = 205;
		public static final int CUSTOM_PAYLOAD = 250;
		public static final int KEY_RESPONSE = 252;
		public static final int GET_INFO = 254;
		public static final int KICK_DISCONNECT = 255;
		
		/**
		 * A registry that parses between names and packet IDs.
		 * @return The current client registry.
		 */
		public static Client getRegistry() {
			return INSTANCE;
		}
		
		// Like above
		private Client() {
			super();
		}
	}
	
	/**
	 * A registry that parses between names and packet IDs.
	 * @return The current client registry.
	 */
	public static Server getServerRegistry() {
		return Server.getRegistry();
	}

	/**
	 * A registry that parses between names and packet IDs.
	 * @return The current server registry.
	 */
	public static Client getClientRegistry() {
		return Client.INSTANCE;
	}
	
	/**
	 * Find a packet by name. Must be capitalized and use underscores.
	 * @param name - name of packet to find.
	 * @return The packet ID found.
	 */
	public static int valueOf(String name) {
		Integer serverAttempt = Server.INSTANCE.valueOf(name);
		
		if (serverAttempt != null)
			return serverAttempt;
		else
			return Client.INSTANCE.valueOf(name);
	}
	
	/**
	 * Retrieves the name of a packet.
	 * @param packetID - packet to retrieve name.
	 * @return The name, or NULL if unable to find such a packet.
	 */
	public static String getDeclaredName(int packetID) {
		String serverAttempt = Server.INSTANCE.getDeclaredName(packetID);
		
		if (serverAttempt != null)
			return serverAttempt;
		else
			return Client.INSTANCE.getDeclaredName(packetID);
	}
}