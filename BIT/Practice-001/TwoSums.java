import java.io.*;
import java.util.*;

//https://leetcode.com/problems/two-sum

public class TwoSums {

	public static void readFile(List<Integer> arr, String fileName) {
		File file = new File(fileName);
		try{
			FileReader fr = new FileReader(file);
			BufferedReader br = new BufferedReader(fr);
			String line = "";
			while((line = br.readLine()) != null){
    			arr.add(Integer.parseInt(line));
			}
		} catch(Exception e){
			System.out.println(e);
		}
	}

	public static int[] twoSums(List<Integer> arr, int target) {
		Map<Integer,Integer> keepTrack = new HashMap<Integer,Integer>(arr.size());
		int[] ans = new int[2];
		for (int i = 0; i < arr.size(); ++i) {
			if (keepTrack.containsKey(target - arr.get(i))) {
				ans[0] = keepTrack.get(target - arr.get(i));
				ans[1] = i;
				keepTrack.clear(); //clearing Hashmap drastically sped up execution in Leetcode
				return ans;
			}
			keepTrack.put(arr.get(i), i);
		}
		return ans;
	}

    public static void main(String[] args) {
		//String fileName = "/home/adam/Documents/Practice/DSA/twoSums/input.txt";
        String fileName = "D:\\Documents\\Practice\\DSA\\twoSums\\input.txt";
        List<Integer> arr = new ArrayList<Integer>(100);
		readFile(arr, fileName);
		System.out.println(Arrays.toString(twoSums(arr, 16)));
    }
}
