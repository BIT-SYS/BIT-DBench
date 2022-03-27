package player;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Read csv file and create a hash map where the key is the season and the value is a list of players objects.
 */
public class PlayerList {
    private HashMap<String, List<HockeyPlayer>> playerMap = new HashMap<>();

    public PlayerList() {
        String line = "";
        String splitBy = ",";

        try {
            BufferedReader br = new BufferedReader(new FileReader("filtered_summary.csv"));
            br.readLine(); //skip the first line.
            List<String> seasons = Arrays.asList("20162017", "20172018", "20182019","20192020", "20202021");
            for (String season: seasons){
                this.playerMap.put(season, new ArrayList<>());} //adding seasons as keys with empty lists as values

            while((line = br.readLine()) != null) {
                String[] playerInfo = line.split(splitBy);
                for (String season: seasons){
                    if (playerInfo[1].equals(season)){ //adding player.Player object to the corresponding season
                        this.playerMap.get(season).add(new HockeyPlayer(playerInfo[0], playerInfo[1],
                                playerInfo[2], playerInfo[3], playerInfo[4], playerInfo[5], playerInfo[6],
                                playerInfo[7], playerInfo[8], playerInfo[9], playerInfo[10]));
                    }
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public HashMap<String, List<HockeyPlayer>> getPlayerMap() {
        return this.playerMap;
    }
}
