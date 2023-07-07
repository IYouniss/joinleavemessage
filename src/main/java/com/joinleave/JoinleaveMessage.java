package com.joinleave;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;

public class JoinleaveMessage extends JavaPlugin implements CommandExecutor, Listener {

    private FileConfiguration playersConfig;
    private File playersFile;
    private Connection connection;
    private boolean mysqlEnabled;



    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        int pluginId = YourBStatsIDHERE;
        Metrics Metrics = new Metrics(this, pluginId);
        PlayerWelcome playerWelcome = new PlayerWelcome(this);
        Bukkit.getPluginManager().registerEvents(playerWelcome, this);
        // Register the command executor for the '/njm' command
        getCommand("njm").setExecutor(this);

        // Register JoinLeaveGUI listener
        JoinLeaveGUI guiListener = new JoinLeaveGUI(this);
        getServer().getPluginManager().registerEvents(guiListener, this);

                playersFile = new File(getDataFolder(), "data.yml");
        if (!playersFile.exists()) {
            saveResource("data.yml", false);

        }
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);

        mysqlEnabled = getConfig().getBoolean("mysql.enabled");
        if (mysqlEnabled) {
            if (setupMySQL()) {
                createTableIfNotExists();
            } else {
                getLogger().severe("Failed to connect to MySQL. Please check your configuration.");
            }
        }
    }

    @Override
    public void onDisable() {
        savePlayersConfig();
        closeMySQLConnection();
    }

    private void savePlayersConfig() {
        try {
            playersConfig.save(playersFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save data.yml: " + e.getMessage());
        }
    }


    private boolean setupMySQL() {
        String host = getConfig().getString("mysql.host");
        int port = getConfig().getInt("mysql.port");
        String database = getConfig().getString("mysql.database");
        String username = getConfig().getString("mysql.username");
        String password = getConfig().getString("mysql.password");

        try {
            connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false", username, password);
            return true;
        } catch (SQLException e) {
            getLogger().severe("Failed to connect to MySQL: " + e.getMessage());
            return false;
        }
    }

    private void closeMySQLConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                getLogger().severe("Failed to close MySQL connection: " + e.getMessage());
            }
        }
    }

    private void createTableIfNotExists() {
        try {
            PreparedStatement statement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS player_messages (uuid VARCHAR(36) PRIMARY KEY, join_message TEXT, leave_message TEXT)");
            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            getLogger().severe("Failed to create player_messages table: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }


        Player player = (Player) sender;

        if (args.length == 1 && args[0].equalsIgnoreCase("gui")) {
            // Open the GUI
            JoinLeaveGUI gui = new JoinLeaveGUI(this);
            gui.openGUI(player);
            return true;
        }


        if (args.length == 1 && args[0].equalsIgnoreCase("set")) {
            // Show the list of available set commands
            displaySetCommands(player);
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("set")) {
            String messageType = args[1].toLowerCase();

            if (args.length == 2) {
                // Show the list of available sub-commands for the specified message type
                displaySubCommands(player, messageType);
                return true;
            }

            String message = ChatColor.translateAlternateColorCodes('&', String.join(" ", args).substring(args[0].length() + args[1].length() + 2));

            if (messageType.equals("join") || messageType.equals("leave")) {
                // Check for permission before allowing the player to set the message
                if (messageType.equals("join") && !player.hasPermission("joinleave.set.join")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to set the join message.");
                    return true;
                }

                if (messageType.equals("leave") && !player.hasPermission("joinleave.set.leave")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to set the leave message.");
                    return true;
                }

                setMessage(player, messageType, message);
                player.sendMessage(ChatColor.DARK_PURPLE + "Your " + messageType + " message has been set.");
            } else {
                player.sendMessage(ChatColor.RED + "Invalid message type. Use 'join' or 'leave'.");
            }

            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            // Check for permission before allowing the player to reload the plugin
            if (!player.hasPermission("joinleave.reload")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to reload the plugin.");
                return true;
            }

            reloadPlugin(player);
            return true;
        }

        displayHelpMenu(player);
        return true;
    }

    private void displaySetCommands(Player player) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_PURPLE + "         NewJoinMessage Set Commands");
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "  /njm set join <message>" + ChatColor.DARK_PURPLE + " - Set your join message");
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "  /njm set leave <message>" + ChatColor.DARK_PURPLE + " - Set your leave message");
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void displaySubCommands(Player player, String messageType) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_PURPLE + "         NewJoinMessage " + capitalize(messageType) + " Commands");
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "  /njm set " + messageType + " <message>" + ChatColor.DARK_PURPLE + " - Set your " + messageType + " message");
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("set");
            subCommands.add("gui");
            subCommands.add("reload");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            List<String> messageTypes = new ArrayList<>();
            messageTypes.add("join");
            messageTypes.add("leave");
            StringUtil.copyPartialMatches(args[1], messageTypes, completions);
        }

        Collections.sort(completions);
        return completions;
    }


    private void reloadPlugin(Player player) {
        reloadConfig();
        reloadPlayersConfig();

        closeMySQLConnection();
        mysqlEnabled = getConfig().getBoolean("mysql.enabled");
        if (mysqlEnabled) {
            if (setupMySQL()) {
                createTableIfNotExists();
            } else {
                getLogger().severe("Failed to connect to MySQL. Please check your configuration.");
            }
        }

        player.sendMessage(ChatColor.DARK_PURPLE + "Plugin reloaded" + ChatColor.GREEN + " ✔" );
    }
    private void displayHelpMenu(Player player) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_PURPLE + "         NewJoinMessage Help");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "    JoinMessage version: " + ChatColor.GREEN + "2.0 Beta" + ChatColor.GREEN + " ✔");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "    Made With " + ChatColor.RED + "❤" + ChatColor.GRAY + " by Yanel");
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "  /njm set join <message>" + ChatColor.DARK_PURPLE + " - Set your join message");
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "  /njm set leave <message>" + ChatColor.DARK_PURPLE + " - Set your leave message");
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "  /njm gui" + ChatColor.DARK_PURPLE + " - To open gui");
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "  /njm reload" + ChatColor.DARK_PURPLE + " - Reload the plugin");
        player.sendMessage("");
        String joinMessage = getMessage(player, "join", "default-join-message");
        String leaveMessage = getMessage(player, "leave", "default-leave-message");

        player.sendMessage(ChatColor.GREEN + "Your current Join Message: " + ChatColor.RESET + (joinMessage.equals(getConfig().getString("default-join-message")) ? ChatColor.GRAY + "Using default message" : joinMessage));
        player.sendMessage(ChatColor.GREEN + "Your current Leave Message: " + ChatColor.RESET + (leaveMessage.equals(getConfig().getString("default-leave-message")) ? ChatColor.GRAY + "Using default message" : leaveMessage));

        player.sendMessage(ChatColor.LIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    @EventHandler
    public void handleJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        String joinPrefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("join-prefix"));
        String defaultJoinPrefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("default-join-prefix").replace("PLAYERNAME", playerName));
        String joinEditable = getMessage(player, "join", "default-join-message");

        String joinMessage = joinPrefix + " " + defaultJoinPrefix + ChatColor.GRAY + " - " + parseMessage(joinEditable, player);

        event.setJoinMessage(joinMessage);
    }

    @EventHandler
    public void handleLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        String leavePrefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("leave-prefix"));
        String defaultLeavePrefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("default-leave-prefix").replace("PLAYERNAME", playerName));
        String leaveEditable = getMessage(player, "leave", "default-leave-message");

        String leaveMessage = leavePrefix + " " + defaultLeavePrefix + ChatColor.GRAY + " - " + parseMessage(leaveEditable, player);

        event.setQuitMessage(leaveMessage);
    }

    private String parseMessage(String message, Player player) {
        String parsedMessage = ChatColor.translateAlternateColorCodes('&', message);

        if (parsedMessage.contains("PLAYERNAME")) {
            parsedMessage = parsedMessage.replace("PLAYERNAME", player.getName());
        }

        return parsedMessage;
    }





    private void reloadPlayersConfig() {
        playersFile = new File(getDataFolder(), "data.yml");
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);
    }

    public void setMessage(Player player, String column, String message) {
        if (mysqlEnabled) {
            try {
                PreparedStatement statement = connection.prepareStatement("INSERT INTO player_messages (uuid, " + column + "_message) VALUES (?, ?) ON DUPLICATE KEY UPDATE " + column + "_message = ?");
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, message);
                statement.setString(3, message);
                statement.executeUpdate();
                statement.close();
            } catch (SQLException e) {
                getLogger().severe("Failed to set " + column + " message for player: " + e.getMessage());
            }
        } else {
            playersConfig.set("players." + player.getUniqueId() + "." + column + "_message", message);
            savePlayersConfig();
        }
    }

    public String getMessage(Player player, String messageType, String defaultMessageType) {
        if (mysqlEnabled) {
            try {
                PreparedStatement statement = connection.prepareStatement("SELECT " + messageType + "_message FROM player_messages WHERE uuid = ?");
                statement.setString(1, player.getUniqueId().toString());
                ResultSet resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    String message = resultSet.getString(messageType + "_message");
                    if (message != null && !message.isEmpty()) {
                        return message;
                    }
                }

                resultSet.close();
                statement.close();
            } catch (SQLException e) {
                getLogger().severe("Failed to retrieve " + messageType + " message for player: " + e.getMessage());
            }
        } else {
            String message = playersConfig.getString("players." + player.getUniqueId() + "." + messageType + "_message");
            if (message != null && !message.isEmpty()) {
                return message;
            }
        }

        return getConfig().getString(defaultMessageType);
    }
}


