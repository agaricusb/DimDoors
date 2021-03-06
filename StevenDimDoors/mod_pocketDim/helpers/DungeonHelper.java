package StevenDimDoors.mod_pocketDim.helpers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.regex.Pattern;

import net.minecraft.util.WeightedRandom;
import net.minecraft.world.World;
import StevenDimDoors.mod_pocketDim.DDProperties;
import StevenDimDoors.mod_pocketDim.DimData;
import StevenDimDoors.mod_pocketDim.DungeonGenerator;
import StevenDimDoors.mod_pocketDim.LinkData;
import StevenDimDoors.mod_pocketDim.mod_pocketDim;
import StevenDimDoors.mod_pocketDim.dungeon.DungeonSchematic;
import StevenDimDoors.mod_pocketDim.dungeon.pack.DungeonPack;
import StevenDimDoors.mod_pocketDim.dungeon.pack.DungeonPackConfig;
import StevenDimDoors.mod_pocketDim.dungeon.pack.DungeonPackConfigReader;
import StevenDimDoors.mod_pocketDim.dungeon.pack.DungeonType;
import StevenDimDoors.mod_pocketDim.items.itemDimDoor;
import StevenDimDoors.mod_pocketDim.util.ConfigurationProcessingException;
import StevenDimDoors.mod_pocketDim.util.WeightedContainer;

public class DungeonHelper
{
	//TODO: File-handling functionality should be spun off to a helper class later
	private static class DirectoryFilter implements FileFilter
	{
		@Override
		public boolean accept(File file)
		{
			return file.isDirectory();
		}
	}
	
	private static class SchematicFileFilter implements FileFilter
	{
		@Override
		public boolean accept(File file)
		{
			return file.isFile() && file.getName().endsWith(SCHEMATIC_FILE_EXTENSION);
		}
	}
	
	private static DungeonHelper instance = null;
	private static DDProperties properties = null;
	
	public static final Pattern SCHEMATIC_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_\\-]+");
	public static final Pattern DUNGEON_NAME_PATTERN = Pattern.compile("[A-Za-z0-9\\-]+");
	
	public static final String SCHEMATIC_FILE_EXTENSION = ".schematic";
	
	private static final String DEFAULT_UP_SCHEMATIC_PATH = "/schematics/core/simpleStairsUp.schematic";
	private static final String DEFAULT_DOWN_SCHEMATIC_PATH = "/schematics/core/simpleStairsDown.schematic";
	private static final String DEFAULT_ERROR_SCHEMATIC_PATH = "/schematics/core/somethingBroke.schematic";
	private static final String DUNGEON_CREATION_GUIDE_SOURCE_PATH = "/mods/DimDoors/text/How_to_add_dungeons.txt";
	private static final String RUINS_PACK_PATH = "/schematics/ruins";
	private static final String BUNDLED_RUINS_LIST_PATH = "/schematics/ruins.txt";
	private static final String STANDARD_CONFIG_FILE_NAME = "rules.txt";
	
	private static final int NETHER_DIMENSION_ID = -1;
	
	private static final int MIN_PACK_SWITCH_CHANCE = 0;
	private static final int PACK_SWITCH_CHANCE_PER_LEVEL = 1;
	private static final int MAX_PACK_SWITCH_CHANCE = 500;
	private static final int START_PACK_SWITCH_CHANCE = MAX_PACK_SWITCH_CHANCE / 9;
	
	private static final int DEFAULT_DUNGEON_WEIGHT = 100;
	public static final int MIN_DUNGEON_WEIGHT = 1; //Prevents MC's random selection algorithm from throwing an exception
	public static final int MAX_DUNGEON_WEIGHT = 10000; //Used to prevent overflows and math breaking down
	
	private static final int MAX_EXPORT_RADIUS = 50;
	public static final short MAX_DUNGEON_WIDTH = 2 * MAX_EXPORT_RADIUS + 1;
	public static final short MAX_DUNGEON_HEIGHT = MAX_DUNGEON_WIDTH;
	public static final short MAX_DUNGEON_LENGTH = MAX_DUNGEON_WIDTH;
	
	private ArrayList<DungeonGenerator> untaggedDungeons = new ArrayList<DungeonGenerator>();
	private ArrayList<DungeonGenerator> registeredDungeons = new ArrayList<DungeonGenerator>();
 
	private DungeonPack RuinsPack;
	private HashMap<String, DungeonPack> dungeonPackMapping = new HashMap<String, DungeonPack>();
	private ArrayList<DungeonPack> dungeonPackList = new ArrayList<DungeonPack>();
	
	private DungeonGenerator defaultUp;
	private DungeonGenerator defaultDown;
	private DungeonGenerator defaultError;
	
	private DungeonHelper()
	{
		//Load our reference to the DDProperties singleton
		if (properties == null)
			properties = DDProperties.instance();
		
		registerDungeons();
	}
	
	public static DungeonHelper initialize()
	{
		if (instance == null)
		{
			instance = new DungeonHelper();
		}
		else
		{
			throw new IllegalStateException("Cannot initialize DungeonHelper twice");
		}
		
		return instance;
	}
	
	public static DungeonHelper instance()
	{
		if (instance == null)
		{
			//This is to prevent some frustrating bugs that could arise when classes
			//are loaded in the wrong order. Trust me, I had to squash a few...
			throw new IllegalStateException("Instance of DungeonHelper requested before initialization");
		}
		return instance;
	}
	
	private void registerDungeons()
	{
		File file = new File(properties.CustomSchematicDirectory);
		if (file.exists() || file.mkdir())
		{
			copyfile.copyFile(DUNGEON_CREATION_GUIDE_SOURCE_PATH, file.getAbsolutePath() + "/How_to_add_dungeons.txt");
		}
		
		DungeonPackConfigReader reader = new DungeonPackConfigReader();		
		registerBundledDungeons(reader);
		registerCustomDungeons(properties.CustomSchematicDirectory, reader);
	}
	
	private static DungeonPackConfig loadDungeonPackConfig(String configPath, String name, boolean isInternal, DungeonPackConfigReader reader)
	{
		try
		{
			DungeonPackConfig config;
			if (isInternal)
			{
				config = reader.readFromResource(configPath);
			}
			else
			{
				config = reader.readFromFile(configPath);
			}
			config.setName(name);
			return config;
		}
		catch (ConfigurationProcessingException e)
		{
			System.err.println(e.getMessage());
			if (e.getCause() != null)
			{
				System.err.println(e.getCause());
			}
		}
		catch (FileNotFoundException e)
		{
			System.err.println("Could not find a dungeon pack config file: " + configPath);
		}
		return null;
	}
	
	private void registerDungeonPack(String directory, Iterable<String> schematics, boolean isInternal, boolean verbose, DungeonPackConfigReader reader)
	{
		//First determine the pack's name and validate it
		File packDirectory = new File(directory);
		String name = packDirectory.getName().toUpperCase();
		//TODO: ADD VALIDATION HERE?
		
		//Check for naming conflicts
		//That could happen if a user has a custom pack with a name that conflicts with a bundled pack,
		//or if a user is running Linux and has two directories with names differing only by capitalization.
		
		DungeonPack pack = dungeonPackMapping.get(name);
		if (pack == null)
		{
			//Load the pack's configuration file
			
			String configPath;
			if (isInternal)
			{
				configPath = directory + "/" + STANDARD_CONFIG_FILE_NAME;
			}
			else
			{
				configPath = directory + File.separator + STANDARD_CONFIG_FILE_NAME;
			}
			DungeonPackConfig config = loadDungeonPackConfig(configPath, name, isInternal, reader);
			if (config == null)
			{
				System.err.println("Could not load config file: " + configPath);
				return;
			}
			
			//Register the pack
			pack = new DungeonPack(config);
			dungeonPackMapping.put(name, pack);
			dungeonPackList.add(pack);
		}
		else
		{
			//Show a warning that there is a naming conflict but keep going. People can use this to extend
			//our built-in packs with custom schematics without tampering with our mod's JAR file.
			System.err.println("A dungeon pack has the same name as another pack that has already been loaded: " + directory);
			System.err.println("We will try to load its schematics but will not check its config file.");
		}
		
		//Register the dungeons! ^_^
		for (String schematicPath : schematics)
		{
			registerDungeon(schematicPath, pack, isInternal, verbose);
		}
	}
	
	public List<DungeonGenerator> getRegisteredDungeons()
	{
		return Collections.unmodifiableList(this.registeredDungeons);
	}
	
	public List<DungeonGenerator> getUntaggedDungeons()
	{
		return Collections.unmodifiableList(this.untaggedDungeons);
	}
	
	public DungeonGenerator getDefaultErrorDungeon()
	{
		return defaultError;
	}
	
	public DungeonGenerator getDefaultUpDungeon()
	{
		return defaultUp;
	}
	
	public DungeonGenerator getDefaultDownDungeon()
	{
		return defaultDown;
	}
	
	public DungeonPack getDungeonPack(String name)
	{
		//TODO: This function might be obsolete after the new save format is implemented.
		return dungeonPackMapping.get(name.toUpperCase());
	}
	
	public DungeonPack getDimDungeonPack(int dimensionID)
	{
		//FIXME: This function is a workaround to our current dungeon data limitations. Modify later.
		//The upcoming save format change and code overhaul will make this obsolete.
		
		DungeonPack pack;
		DungeonGenerator generator = dimHelper.dimList.get(dimensionID).dungeonGenerator;
		if (generator != null)
		{
			pack = generator.getDungeonType().Owner;
			
			//Make sure the pack isn't null. This can happen for dungeons with the special UNKNOWN type.
			if (pack == null)
			{
				pack = RuinsPack;
			}
		}
		else
		{
			if (dimensionID == NETHER_DIMENSION_ID)
			{
				//TODO: Change this to the nether-side pack later ^_^
				pack = RuinsPack;
			}
			else
			{
				pack = RuinsPack;
			}
		}
		return pack;
	}
	
	public LinkData createCustomDungeonDoor(World world, int x, int y, int z)
	{
		//Create a link above the specified position. Link to a new pocket dimension.
		LinkData link = new LinkData(world.provider.dimensionId, 0, x, y + 1, z, x, y + 1, z, true, 3);
		link = dimHelper.instance.createPocket(link, true, false);
		
		//Place a Warp Door linked to that pocket
		itemDimDoor.placeDoorBlock(world, x, y, z, 3, mod_pocketDim.ExitDoor);
		
		return link;
	}
	
	public boolean validateDungeonType(String type, DungeonPack pack)
	{
		//Check if the dungeon type is valid
		return pack.isKnownType(type);
	}
	
	public boolean validateSchematicName(String name, DungeonPack pack)
	{
		String[] dungeonData;
		
		if (!name.endsWith(SCHEMATIC_FILE_EXTENSION))
			return false;
		
		dungeonData = name.substring(0, name.length() - SCHEMATIC_FILE_EXTENSION.length()).split("_");

		//Check for a valid number of parts
		if (dungeonData.length < 3 || dungeonData.length > 4)
			return false;

		//Check if the dungeon type is valid
		if (!validateDungeonType(dungeonData[0], pack))
			return false;
		
		//Check if the name is valid
		if (!SCHEMATIC_NAME_PATTERN.matcher(dungeonData[1]).matches())
			return false;
		
		//Check if the open/closed flag is present
		if (!dungeonData[2].equalsIgnoreCase("open") && !dungeonData[2].equalsIgnoreCase("closed"))
			return false;
		
		//If the weight is present, check that it is valid
		if (dungeonData.length == 4)
		{
			try
			{
				int weight = Integer.parseInt(dungeonData[3]);
				if (weight < MIN_DUNGEON_WEIGHT || weight > MAX_DUNGEON_WEIGHT)
					return false;
			}
			catch (NumberFormatException e)
			{
				//Not a number
				return false;
			}
		}
		return true;
	}
	
	public void registerDungeon(String schematicPath, DungeonPack pack, boolean isInternal, boolean verbose)
	{
		//We use schematicPath as the real path for internal files (inside our JAR) because it seems
		//that File tries to interpret it as a local drive path and mangles it.
		File schematicFile = new File(schematicPath);
		String name = schematicFile.getName();
		String path = isInternal ? schematicPath : schematicFile.getAbsolutePath();
		try
		{
			if (validateSchematicName(name, pack))
			{
				//Strip off the file extension while splitting the file name
				String[] dungeonData = name.substring(0, name.length() - SCHEMATIC_FILE_EXTENSION.length()).split("_");
				
				DungeonType dungeonType = pack.getType(dungeonData[0]);
				boolean isOpen = dungeonData[2].equalsIgnoreCase("open");
				int weight = (dungeonData.length == 4) ? Integer.parseInt(dungeonData[3]) : DEFAULT_DUNGEON_WEIGHT;
				
				//Add this custom dungeon to the list corresponding to its type
				DungeonGenerator generator = new DungeonGenerator(weight, path, isOpen, dungeonType);

				pack.addDungeon(generator);
				registeredDungeons.add(generator);
				if (verbose)
				{
					System.out.println("Registered dungeon: " + name);
				}
			}
			else
			{
				if (verbose)
				{
					System.out.println("The following dungeon name is invalid for its given pack. It will not be generated naturally: " + schematicPath);
				}
				untaggedDungeons.add(new DungeonGenerator(DEFAULT_DUNGEON_WEIGHT, path, true, DungeonType.UNKNOWN_TYPE));
				System.out.println("Registered untagged dungeon: " + name);
			}
		}
		catch (Exception e)
		{
			System.err.println("Failed to register dungeon: " + name);
			e.printStackTrace();
		}
	}

	private void registerCustomDungeons(String path, DungeonPackConfigReader reader)
	{
		File[] schematics;
		File[] packDirectories;
		File[] packFiles;
		ArrayList<String> packFilePaths;
		File directory = new File(path);
		SchematicFileFilter schematicFileFilter = new SchematicFileFilter();

		//Check that the Ruins pack has been loaded
		if (RuinsPack == null)
		{
			throw new IllegalStateException("Cannot register custom dungeons without first loading the Ruins dungeon pack.");
		}
		
		//Load stray dungeons directly in the custom dungeons folder
		schematics = directory.listFiles(schematicFileFilter);
		if (schematics != null)
		{
			for (File schematicFile : schematics)
			{
				registerDungeon(schematicFile.getPath(), RuinsPack, false, true);
			}
		}
		else
		{
			System.err.println("Could not retrieve the list of schematics stored in the custom dungeons directory!");
		}
		schematics = null; //Release memory
		
		//Load the custom dungeon packs
		packDirectories = directory.listFiles(new DirectoryFilter());
		if (packDirectories != null)
		{
			//Loop through each directory, which is assumed to be a dungeon pack
			for (File packDirectory : packDirectories)
			{
				//List the schematics within the dungeon pack directory
				packFiles = packDirectory.listFiles(schematicFileFilter);
				if (packFiles != null)
				{
					//Copy the pack files' paths into an ArrayList for use with registerDungeonPack()
					packFilePaths = new ArrayList<String>(packFiles.length);
					for (File packFile : packFiles)
					{
						packFilePaths.add(packFile.getPath());
					}
					
					registerDungeonPack(packDirectory.getAbsolutePath(), packFilePaths, false, true, reader);
				}
				else
				{
					System.err.println("Could not retrieve the list of schematics in a dungeon pack: " + packDirectory.getPath());
				}
			}
		}
		else
		{
			System.err.println("Could not retrieve the list of dungeon pack directories in the custom dungeons directory!");
		}
	}

	private void registerBundledDungeons(DungeonPackConfigReader reader)
	{
		//Register the core schematics
		//These are used for debugging and in case of unusual errors while loading dungeons
		defaultUp = new DungeonGenerator(DEFAULT_DUNGEON_WEIGHT, DEFAULT_UP_SCHEMATIC_PATH, true, DungeonType.UNKNOWN_TYPE);
		defaultDown = new DungeonGenerator(DEFAULT_DUNGEON_WEIGHT, DEFAULT_DOWN_SCHEMATIC_PATH, true, DungeonType.UNKNOWN_TYPE);
		defaultError = new DungeonGenerator(DEFAULT_DUNGEON_WEIGHT, DEFAULT_ERROR_SCHEMATIC_PATH, true, DungeonType.UNKNOWN_TYPE);
		
		//Open the list of dungeons packaged with our mod and register their schematics
		registerBundledPack(BUNDLED_RUINS_LIST_PATH, RUINS_PACK_PATH, "Ruins", reader);
		RuinsPack = getDungeonPack("Ruins");
		
		System.out.println("Finished registering bundled dungeon packs");
	}
	
	private void registerBundledPack(String listPath, String packPath, String name, DungeonPackConfigReader reader)
	{
		System.out.println("Registering bundled dungeon pack: " + name);
		
		InputStream listStream = this.getClass().getResourceAsStream(listPath);
		if (listStream == null)
		{
			System.err.println("Unable to open list of bundled dungeon schematics for " + name);
			return;
		}
		
		try
		{
			//Read the list of schematics that come with a bundled pack
			BufferedReader listReader = new BufferedReader(new InputStreamReader(listStream));
			ArrayList<String> schematics = new ArrayList<String>();
			String schematicPath = listReader.readLine();
			while (schematicPath != null)
			{
				schematicPath = schematicPath.trim();
				if (!schematicPath.isEmpty())
				{
					schematics.add(schematicPath);
				}
				schematicPath = listReader.readLine();
			}
			listReader.close();
			
			//Register the pack
			registerDungeonPack(packPath, schematics, true, false, reader);
		}
		catch (Exception e)
		{
			System.err.println("An exception occurred while reading the list of bundled dungeon schematics for " + name);
			e.printStackTrace();
		}
	}

	public boolean exportDungeon(World world, int centerX, int centerY, int centerZ, String exportPath)
	{
		//Write schematic data to a file
		try
		{
			DungeonSchematic dungeon = DungeonSchematic.copyFromWorld(world,
					centerX - MAX_EXPORT_RADIUS, centerY - MAX_EXPORT_RADIUS, centerZ - MAX_EXPORT_RADIUS,
					MAX_DUNGEON_WIDTH, MAX_DUNGEON_HEIGHT, MAX_DUNGEON_LENGTH, true);
			dungeon.applyExportFilters(properties);
			dungeon.writeToFile(exportPath);
			return true;
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return false;
		}
	}

	public void generateDungeonLink(LinkData inbound, DungeonPack pack, Random random)
	{
		DungeonGenerator selection;
		DungeonPackConfig config;
		DungeonPack selectedPack;
		
		try
		{
			config = pack.getConfig();
			selectedPack = pack;
			
			//Are we allowed to switch to another dungeon pack?
			if (config.allowPackChangeOut())
			{
				//Calculate the chance of switching to a different pack type
				int packSwitchChance;
				if (dimHelper.dimList.get(inbound.locDimID).depth == 0)
				{
					packSwitchChance = START_PACK_SWITCH_CHANCE;
				}
				else
				{
					packSwitchChance = MIN_PACK_SWITCH_CHANCE + (getPackDepth(inbound, pack) - 1) * PACK_SWITCH_CHANCE_PER_LEVEL;
				}

				//Decide randomly whether to switch packs or not
				if (random.nextInt(MAX_PACK_SWITCH_CHANCE) < packSwitchChance)
				{
					//Find another pack
					selectedPack = getRandomDungeonPack(pack, random);
				}
			}
			
			//Pick the next dungeon
			selection = selectedPack.getNextDungeon(inbound, random);
		}
		catch (Exception e)
		{
			System.err.println("An exception occurred while selecting a dungeon:");
			e.printStackTrace();
			
			if (!pack.isEmpty())
			{
				selection = pack.getRandomDungeon(random);
			}
			else
			{
				selection = defaultError;
			}
		}
		dimHelper.instance.getDimData(inbound.destDimID).dungeonGenerator = selection;
	}

	@SuppressWarnings("unchecked")
	private DungeonPack getRandomDungeonPack(DungeonPack current, Random random)
	{
		DungeonPack selection = current;
		ArrayList<WeightedContainer<DungeonPack>> packs = new ArrayList<WeightedContainer<DungeonPack>>(dungeonPackList.size());

		//Load up a list of weighted items with any usable dungeon packs that is not the current pack
		for (DungeonPack pack : dungeonPackList)
		{
			DungeonPackConfig config = pack.getConfig();
			if (pack != current && config.allowPackChangeIn() && !pack.isEmpty())
			{
				packs.add(new WeightedContainer<DungeonPack>(pack, config.getPackWeight()));
			}
		}
		if (!packs.isEmpty())
		{
			//Pick a random dungeon pack
			selection = ((WeightedContainer<DungeonPack>) WeightedRandom.getRandomItem(random, packs)).getData();
		}
		return selection;
	}

	public Collection<String> getDungeonNames() {

		//Use a HashSet to guarantee that all dungeon names will be distinct.
		//This shouldn't be necessary if we keep proper lists without repetitions,
		//but it's a fool-proof workaround.
		HashSet<String> dungeonNames = new HashSet<String>();
		dungeonNames.addAll( parseDungeonNames(registeredDungeons) );
		dungeonNames.addAll( parseDungeonNames(untaggedDungeons) );
		
		//Sort dungeon names alphabetically
		ArrayList<String> sortedNames = new ArrayList<String>(dungeonNames);
		Collections.sort(sortedNames, String.CASE_INSENSITIVE_ORDER);
		return sortedNames;
	}
	
	private static ArrayList<String> parseDungeonNames(ArrayList<DungeonGenerator> dungeons)
	{
		String name;
		File schematic;
		ArrayList<String> names = new ArrayList<String>(dungeons.size());
		
		for (DungeonGenerator dungeon : dungeons)
		{
			//Retrieve the file name and strip off the file extension
			schematic = new File(dungeon.schematicPath);
			name = schematic.getName();
			name = name.substring(0, name.length() - SCHEMATIC_FILE_EXTENSION.length());
			names.add(name);
		}
		return names;
	}
	
	public static ArrayList<DungeonGenerator> getDungeonChainHistory(DimData dimData, DungeonPack pack, int maxSize)
	{
		//TODO: I've improved this code for the time being. However, searching across links is a flawed approach. A player could
		//manipulate the output of this function by setting up links to mislead our algorithm or by removing links.
		//Dimensions MUST have built-in records of their parent dimensions in the future. ~SenseiKiwi
		
		ArrayList<DungeonGenerator> history = new ArrayList<DungeonGenerator>();
		DimData tailDim = dimData;
		boolean found = true;
		
		if (dimData.dungeonGenerator == null || dimData.dungeonGenerator.getDungeonType().Owner != pack || maxSize < 1)
		{
			//The initial dimension is already outside our pack. Return an empty list.
			return history;
		}
		history.add(dimData.dungeonGenerator);
		
		for (int count = 1; count < maxSize && found; count++)
		{
			found = false;
			for (LinkData link : tailDim.getLinksInDim())
			{
				DimData neighbor = dimHelper.instance.getDimData(link.destDimID);
				if (neighbor.depth == tailDim.depth - 1 && neighbor.dungeonGenerator != null &&
						neighbor.dungeonGenerator.getDungeonType().Owner == pack)
				{
					tailDim = neighbor;
					history.add(tailDim.dungeonGenerator);
					found = true;
					break;
				}
			}
		}
		return history;
	}
	
	private static int getPackDepth(LinkData inbound, DungeonPack pack)
	{
		//TODO: I've improved this code for the time being. However, searching across links is a flawed approach. A player could
		//manipulate the output of this function by setting up links to mislead our algorithm or by removing links.
		//Dimensions MUST have built-in records of their parent dimensions in the future. ~SenseiKiwi
		//Dimensions should also just keep track of pack depth internally.
		
		int packDepth = 1;
		DimData tailDim = dimHelper.dimList.get(inbound.destDimID);
		boolean found;
		
		do
		{
			found = false;
			for (LinkData link : tailDim.getLinksInDim())
			{
				DimData neighbor = dimHelper.instance.getDimData(link.destDimID);
				if (neighbor.depth == tailDim.depth - 1 && neighbor.dungeonGenerator != null &&
						neighbor.dungeonGenerator.getDungeonType().Owner == pack)
				{
					tailDim = neighbor;
					found = true;
					packDepth++;
					break;
				}
			}
		}
		while (found);
		
		return packDepth;
	}
	
	public static ArrayList<DungeonGenerator> getFlatDungeonTree(DimData dimData, int maxSize)
	{
		//TODO: I've improved this code for the time being. However, searching across links is a flawed approach. A player could
		//manipulate the output of this function by setting up links to mislead our algorithm or by removing links.
		//Dimensions MUST have built-in records of their parent dimensions in the future. ~SenseiKiwi
		
		dimHelper helper = dimHelper.instance;
		ArrayList<DungeonGenerator> dungeons = new ArrayList<DungeonGenerator>();
		DimData root = helper.getDimData(helper.getLinkDataFromCoords(dimData.exitDimLink.destXCoord, dimData.exitDimLink.destYCoord, dimData.exitDimLink.destZCoord, dimData.exitDimLink.destDimID).destDimID);
		HashSet<DimData> checked = new HashSet<DimData>();
		Queue<DimData> pendingDimensions = new LinkedList<DimData>();
		
		if (root.dungeonGenerator == null)
		{
			return dungeons;
		}
		pendingDimensions.add(root);
		checked.add(root);
		
		while (dungeons.size() < maxSize && !pendingDimensions.isEmpty())
		{
			DimData current = pendingDimensions.remove();
			for (LinkData link : current.getLinksInDim())
			{
				DimData child = helper.getDimData(link.destDimID);
				if (child.depth == current.depth + 1 && child.dungeonGenerator != null && checked.add(child))
				{
					dungeons.add(child.dungeonGenerator);
					pendingDimensions.add(child);
				}
				if (dungeons.size() == maxSize)
				{
					break;
				}
			}
		}
		return dungeons;
	}
}