package player;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

/**
 * A class that can read a .csv file containing data about tennis players that participated in different tournaments
 * for a specific year (2019), and store that data as a value in a HashMap with the name of the tournament as a key.
 */
public class TennisPlayerList {
    private final HashMap<String, ArrayList<TennisPlayer>> competitionToPlayers;
    static final int TOURNAMENT_NAME = 0;
    static final int WINNER_NAME = 1;
    static final int WINNER_AGE = 3;
    static final int WINNER_COUNTRY = 2;
    static final int LOSER_NAME = 4;
    static final int LOSER_AGE = 6;
    static final int LOSER_COUNTRY = 5;
    static final int WINNER_ACES = 7;
    static final int WINNER_DOUBLE_FAULTS = 8;
    static final int WINNER_SERVE_POINTS = 9;
    static final int WINNER_FIRST_SERVES = 10;
    static final int WINNER_BREAK_POINTS = 11;
    static final int LOSER_ACES = 12;
    static final int LOSER_DOUBLE_FAULTS = 13;
    static final int LOSER_SERVE_POINTS = 14;
    static final int LOSER_FIRST_SERVES = 15;
    static final int LOSER_BREAK_POINTS = 16;

    /**
     * Construct a map mapping tennis players to a competition that happened in a specific year (2019)
     */
    public TennisPlayerList() {
        competitionToPlayers = new HashMap<>();
        String line;
        try {
            BufferedReader reader = new BufferedReader(new FileReader("Sample_Tennis_Data_2019.csv"));
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] playerData = line.split(",");
                if (!(competitionToPlayers.containsKey(playerData[TOURNAMENT_NAME]))) {
                    competitionToPlayers.put(playerData[TOURNAMENT_NAME], new ArrayList<>());
                }
                ArrayList<TennisPlayer> competitionPlayers = competitionToPlayers.get(playerData[TOURNAMENT_NAME]);
                TennisPlayer winner = findTennisPlayer(competitionPlayers, playerData[WINNER_NAME],
                        (int) Math.round(Double.parseDouble(playerData[WINNER_AGE])), playerData[WINNER_COUNTRY]);
                TennisPlayer loser = findTennisPlayer(competitionPlayers, playerData[LOSER_NAME],
                        (int) Math.round(Double.parseDouble(playerData[LOSER_AGE])), playerData[LOSER_COUNTRY]);
                updatePlayer(playerData, winner, WINNER_ACES, WINNER_DOUBLE_FAULTS, WINNER_SERVE_POINTS,
                        WINNER_FIRST_SERVES, WINNER_BREAK_POINTS);
                updatePlayer(playerData, loser, LOSER_ACES, LOSER_DOUBLE_FAULTS, LOSER_SERVE_POINTS,
                        LOSER_FIRST_SERVES, LOSER_BREAK_POINTS);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This is a helper method for the constructor; it updates all that attributes associated with a given
     * tennis player
     * @param playerData a list that contains all the data about two players
     * @param player the player that needs to be updated
     * @param aces playerData[aces] contains the number of new aces made by player
     * @param doubleFaults playerData[doubleFaults] contains the number of new double faults made by player
     * @param servePoints playerData[servePoints] contains the number of new serve points won by player
     * @param firstServes playerData[firstServes] contains the number of new first serves made by player
     * @param breakPoints playerData[breakPoints] contains the number of new break points saved by player
     */
    private void updatePlayer(String[] playerData, TennisPlayer player, int aces, int doubleFaults, int servePoints,
                              int firstServes, int breakPoints) {
        player.updateAces(Integer.parseInt(playerData[aces]));
        player.updateDoubleFaults(Integer.parseInt(playerData[doubleFaults]));
        player.updateServePoints(Integer.parseInt(playerData[servePoints]));
        player.updateFirstServes(Integer.parseInt(playerData[firstServes]));
        player.updateBreakPointsSaved(Integer.parseInt(playerData[breakPoints]));
    }


    /**
     * This is a helper method for the constructor; if a tennis player with the given name, age, and nationality
     * is already in the list of tennis players, that tennis player is found and returned. If that tennis player is
     * not in the list of tennis players, that player is added to the list and returned.
     * @param players list of tennis players
     * @param name tennis player's name
     * @param age tennis player's age
     * @param nationality tennis player's nationality
     * @return a Tennis player from players if the player is there, or a new Tennis player if the player is not there
     */
    private TennisPlayer findTennisPlayer(ArrayList<TennisPlayer> players, String name, int age, String nationality) {
        TennisPlayer newPlayer = new TennisPlayer(name, age, nationality);
        for (TennisPlayer player : players) {
            if (player.equals(newPlayer)) {
                return player;
            }
        }
        players.add(newPlayer);
        return newPlayer;
    }


    /**
     * Return a HashMap of competitions and associated players
     * @return HashMap of players
     */
    public HashMap<String, ArrayList<TennisPlayer>> getAllTennisPlayers() {
        return competitionToPlayers;
    }


    /**
     * Return true if this tennis player and competition can be found in competitionToPlayers
     * @param competition the name of the competition
     * @param name the name of the tennis player
     * @return true if the tennis player is found, and false otherwise
     */
    public boolean containsPlayer(String competition, String name) {
        if (competitionToPlayers.containsKey(competition)) {
            for (TennisPlayer player : competitionToPlayers.get(competition)) {
                if (player.getName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Return a string representation of a player in a given competition
     * @param competition name of the competition
     * @param name name of the player
     * @return string representation of the tennis player
     * @throws Exception if the tennis player or competition are not found
     */
    public TennisPlayer findTennisPlayer(String competition, String name) throws Exception {
        if (!(competitionToPlayers.containsKey(competition))) {
            throw new Exception("Competition not found!");
        } else if (containsPlayer(competition, name)) {
            for (TennisPlayer player : competitionToPlayers.get(competition)) {
                if (Objects.equals(player.getName(), name)) {
                    return player;
                }
            }
        }
        throw new Exception("player.Player not found!");
    }
}
