package StevenDimDoors.mod_pocketDim;

import java.util.ArrayList;
import java.util.Collection;

import StevenDimDoors.mod_pocketDim.helpers.dimHelper;

import net.minecraft.network.INetworkManager;
import net.minecraft.network.NetLoginHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet1Login;
import net.minecraft.server.MinecraftServer;
import cpw.mods.fml.common.network.IConnectionHandler;
import cpw.mods.fml.common.network.Player;

public class ConnectionHandler  implements IConnectionHandler
{
	private static boolean connected = false;
	private static DDProperties properties = null;
		
	//sends a packet to clients containing all the information about the dims and links. Lots of packets, actually. 
	@Override
	public String connectionReceived(NetLoginHandler netHandler, INetworkManager manager) 
	{
		if (properties == null)
			properties = DDProperties.instance();
		
		Collection set = new ArrayList();
		set.addAll(dimHelper.dimList.keySet());	
		PacketHandler.onClientJoinPacket(manager, dimHelper.dimList);
		PacketHandler.onDimCreatedPacket(new DimData(properties.LimboDimensionID, false, 0, 0, 0, 0, 0));
		return null;
	}

	@Override
	public void connectionOpened(NetHandler netClientHandler, String server,int port, INetworkManager manager) 
	{
		connected = true;		
	}

	@Override
	public void connectionOpened(NetHandler netClientHandler,MinecraftServer server, INetworkManager manager) 
	{
	
	}

	@Override
	public void connectionClosed(INetworkManager manager) 
	{
		if (connected)
		{
			System.out.println("Clearing dim cache");
			dimHelper.instance.save();
			dimHelper.instance.unregsisterDims();
			dimHelper.dimList.clear();
		    
		}
		connected = false;
	
	}

	@Override
	public void clientLoggedIn(NetHandler clientHandler, INetworkManager manager,
		Packet1Login login) 
	{
		
	
	
	}

	@Override
	public void playerLoggedIn(Player player, NetHandler netHandler,
		INetworkManager manager) 
	{
		
		

	
	
	}
}