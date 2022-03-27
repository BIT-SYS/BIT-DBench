package dev._2lstudios.skywars.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public class ConfigurationUtil {
  private final Plugin plugin;
  
  public ConfigurationUtil(Plugin plugin) {
    this.plugin = plugin;
  }
  
  public YamlConfiguration getConfiguration(String filePath) {
    File dataFolder = this.plugin.getDataFolder();
    File file = new File(filePath.replace("%datafolder%", dataFolder.toPath().toString()));
    if (file.exists())
      return YamlConfiguration.loadConfiguration(file); 
    return new YamlConfiguration();
  }
  
  public void createConfiguration(String file) {
    Logger logger = this.plugin.getLogger();
    File dataFolder = this.plugin.getDataFolder();
    File configFile = new File(file.replace("%datafolder%", dataFolder.toPath().toString()));
    String configFileName = configFile.getName();
    if (!configFile.exists()) {
      try {
        InputStream inputStream = this.plugin.getClass().getClassLoader().getResourceAsStream(configFileName);
        File parentFile = configFile.getParentFile();
        if (parentFile != null)
          parentFile.mkdirs(); 
        if (inputStream != null) {
          Files.copy(inputStream, configFile.toPath(), new java.nio.file.CopyOption[0]);
          logger.info("File '" + configFile + "' had been created!");
        } else {
          configFile.createNewFile();
        } 
        inputStream.close();
      } catch (Exception e) {
        logger.info("An exception was caught while creating '" + configFileName + "'!");
      } 
    } else {
      logger.info("Skipped '" + configFileName + "' creation because it already exists!");
    } 
  }
  
  public void saveConfiguration(YamlConfiguration yamlConfiguration, String file) {
    this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
          try {
            File dataFolder = this.plugin.getDataFolder();
            yamlConfiguration.save(file.replace("%datafolder%", dataFolder.toPath().toString()));
          } catch (IOException e) {
            this.plugin.getLogger().info("[%pluginname%] Unable to save configuration file!".replace("%pluginname%", this.plugin.getDescription().getName()));
          } 
        });
  }
  
  public void deleteConfiguration(String file) {
    this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
          File file1 = new File(file);
          if (file1.exists())
            file1.delete(); 
        });
  }
}
