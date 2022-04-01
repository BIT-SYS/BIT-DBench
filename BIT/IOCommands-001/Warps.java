package com.interordi.iocommands.modules;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.interordi.iocommands.IOCommands;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

public class Warps {
	
	private String file;
	private Map< String, Warp > warps;
	IOCommands plugin;
	
	
	public Warps(IOCommands plugin) {
		this.plugin = plugin;
		this.file = plugin.getDataFolder().toString() + File.separatorChar + "warps.csv";
		this.warps = new HashMap< String, Warp>();
		
		load();
		//TODO: Load and save the UUID of the person that created the wrap?
	}
	
	
	//Load the list of warps from the file
	public void load() {
		CSVReader reader = null;
		try {
			reader = new CSVReader(new FileReader(this.file));
		} catch (FileNotFoundException e) {
			System.err.println("Failed to load the warps file");
			e.printStackTrace();
			return;
		}
		
		try {
			String[] line;
			while ((line = reader.readNext()) != null) {
				Warp warp = new Warp();
				warp.creator = UUID.fromString(line[2]);
				warp.location = new Location(
					Bukkit.getServer().getWorld(line[1]),
					Double.parseDouble(line[3]), Double.parseDouble(line[4]), Double.parseDouble(line[5]),
					Float.parseFloat(line[7]), Float.parseFloat(line[6])
				);
				warps.put(line[0].toLowerCase(), warp);
			}
			reader.close();
		} catch (IOException e) {
			System.err.println("Failed to read from the warps file");
			e.printStackTrace();
			return;
		}
	}
	
	
	//Save the list of warps to the file
	@SuppressWarnings("deprecation")
	public void save() {
		CSVWriter writer = null;
		try {
			writer = new CSVWriter(new FileWriter(this.file), ',');
		} catch (IOException e) {
			System.err.println("Failed to load the warps file");
			e.printStackTrace();
			return;
		}
		
		for(Map.Entry< String, Warp > entry: warps.entrySet()) {
			String creator = entry.getValue().creator.toString();
			Location pos = entry.getValue().location;

			//Skip unknown worlds
			try {
				if (pos.getWorld() == null)
					continue;
			} catch (IllegalArgumentException e) {
				return;
			}

			String[] line = {
					entry.getKey().toString().toLowerCase(),
					pos.getWorld().getName(),
					creator,
					String.valueOf(pos.getX()), String.valueOf(pos.getY()), String.valueOf(pos.getZ()),
					String.valueOf(pos.getPitch()), String.valueOf(pos.getYaw())
			};
			writer.writeNext(line);
		}
		
		try {
			writer.close();
		} catch (IOException e) {
			System.err.println("Failed to write to the warps file");
			e.printStackTrace();
			return;
		}
	}
	
	
	//Get a wrap based on its name
	public Warp getWarp(String name) {
		return warps.get(name.toLowerCase());
	}
	
	
	//Add a warp or update an existing one
	public void setWarp(Player creator, String name, Location pos) {
		Warp warp = new Warp();
		warp.creator = creator.getUniqueId();
		warp.location = pos;
		warps.put(name.toLowerCase(), warp);
		save();
	}

}
