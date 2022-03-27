import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.math3.special.Erf; //erfc function for Monobit test
import org.jtransforms.fft.DoubleFFT_1D; //Fourier Transform function for NIST test

public class EntropyProcessor {
	
	/*
	 * NOTE: Any additional information we need on the mouse inputs (timing for instance) can be added to the Pair class
	 */
	public static void main(String[] args) {
		if (args.length <= 1 || args.length >= 4) {
			System.err.println("ERROR: Must supply filenames and confidence value as a command line argument");
			System.exit(-1);
		}
		if (args.length == 2) { // For when you just give an input file and confidence value
			String filename = args[0];
			double confidence = Double.parseDouble(args[1]);
			
			ArrayList<Pair> data = readData(filename);
			
			process(data);
			
			double entropyEstimate = estimate(data);
			
			//String entropy = mix(processedData);
			
			//boolean testResult = test(entropy, confidence);
			
			System.out.println(frequencyNIST(pairToBits(data), confidence));
			System.out.println(runsNIST(pairToBits(data), confidence));
			System.out.println(fourierNIST(pairToBits(data), confidence));
		}
		else { // For when you give a input file, confidence value, and output file
			String inputFilename = args[0];
			String outputFilename = args[2];
			
			ArrayList<Pair> data = readData(inputFilename);
			
			try {
				FileWriter writer = new FileWriter(outputFilename);
				writer.write(pairToBits(data));
				writer.close();
			} catch (IOException e) {
				System.out.println("Something went screwy writing to file.");
				e.printStackTrace();
			}
			
		}
		
		// Print test result or something
	}
	
	/*
	 * Calculates log base 2 of a given integer as a double.
	 * Implemented by Joseph
	 */
	public static double log2(double x) {
		return Math.log(x) / Math.log(2.0);
	}
	
	/*
	 * Reads the filename and creates pairs of integers to represent the mouse data from that file
	 * Implemented by Joseph
	 */
	public static ArrayList<Pair> readData(String filename) {
		ArrayList<Pair> pairs = new ArrayList<Pair>();
		try {
			File coordinates = new File(filename);
			Scanner reader = new Scanner(coordinates);
			Pattern p = Pattern.compile("\\d+");
			Matcher m = null;
			int x = 0, y = 0;
			while (reader.hasNextLine()) {
				String line = reader.nextLine();
				m = p.matcher(line);
				m.find();
				x = Integer.parseInt(m.group());
				m.find();
				y = Integer.parseInt(m.group());
				pairs.add(new Pair(x, y));
			}
		} catch (FileNotFoundException e) {
			System.err.println("ERROR: coordinates.txt not found");
			System.exit(-1);
		}
		return pairs;
	}
	
	/*
	 * Takes an array list of pairs of integers representing (X, Y) mouse coordinates and processes the data
	 * For instance, removing pairs at (0, 0).
	 * Returns a processed ArrayList of pairs of xintegers representing (X, Y) mouse coordinates
	 */
	public static ArrayList<Pair> process(ArrayList<Pair> data) {
		for (int i = 0; i < data.size(); i++) {
			if (data.get(i).x == 960 & data.get(i).y == 540) {
				data.remove(i);
			}
		}
		
		return data;
	}
	
	/*
	 * Takes an ArrayList of pairs of integers representing (X, Y) mouse coordinates and returns a double representing the estimated amount of entropy from the data
	 * Implemented by Joseph
	 */
	public static double estimate(ArrayList<Pair> data) {
		// Count frequency of x, y pairs
		HashMap<Pair, Integer> frequency = new HashMap<Pair, Integer>();
		for (int i = 0; i < data.size(); i++) {
			if (frequency.containsKey(data.get(i))) {
				frequency.put(data.get(i), frequency.get(data.get(i)) + 1);
			}
			else {
				frequency.put(data.get(i), 1);
			}
		}
		// Sum the frequency of all pairs multiplied by their log(frequency) and multiply by negative one to calculate shannon entropy
		double sum = 0.0;
		double highestFrequency = 0.0;
		double probabilityOfPair = 0.0;
		
		// Take the amount of occurrences of each pair (i) and divide it by the total size of the data.  Then add it to the sum of shannon entropy by multiplying it by its log2
		for (Integer i : frequency.values()) {
			probabilityOfPair = (double)i / (double)data.size();
			if (probabilityOfPair > highestFrequency) {
				highestFrequency = probabilityOfPair;
			}
			sum += probabilityOfPair * log2(probabilityOfPair);
		}
		
		sum *= -1;
		System.out.println("Entropy Estimate = " + sum);
		
		double minEntropy = -1 * log2(highestFrequency);
		
		System.out.println("minEntropy = " + minEntropy);
		
		return sum;
	}
	
	/*
	 * Takes an ArrayList of pairs of integers representing (X, Y) mouse coordinates and returns the string of entropy generated from this
	 * NOTE: String return type is a placeholder, return whatever data type would be most appropriate for representing entropy
	 */
	public static String mix(ArrayList<Pair> data) {
		int output = 0;
		int[] input = pairToBits2(data);
		int len = data.size();
		int shift = 0;

		for (int i=0; i < len -3; i++){
			int a = input[i];
			int b = input[i+1];

			input[i] = a + b;
			input[i+1] = Integer.rotateRight(b, shift);

			a = input[i];
			b = input[i+1];

			input[i+3] = a ^ b;

			shift = shift + 3;
			output = input[i+3];
		}
		return Integer.toBinaryString(output);
	}
	
	/*
	 * Takes a list of entropy strings and a confidence value and applies NIST tests to them to determine if the strings are random/unpredictable up to the confidence level
	 */
	public static boolean test(String entropy, double confidence) {
		frequencyNIST(entropy, confidence);
		runsNIST(entropy, confidence);
		fourierNIST(entropy, confidence);
		return false;
	}
	
	// Function to transform pairs of bits to a concatenated string of their binary equivalent.
	// Implemented by Aleks
	public static String pairToBits(ArrayList<Pair> data) {
		String bits = new String();
		
		int length = data.size();
		
		for (int i = 0; i < length; i++) {
			bits += Integer.toBinaryString(data.get(i).x);
			bits += Integer.toBinaryString(data.get(i).y);
		}
		return bits;
	}

	// Function which takes pair input and convert it to list of X+Y integers
	public static int[] pairToBits2(ArrayList<Pair> data) {
		String bits = new String();
		int length = data.size();
		int[] out = new int[length];

		for (int i = 0; i < length; i++) {
			bits = "";
			bits += Integer.toString(data.get(i).x);
			bits += Integer.toString(data.get(i).y);
			out[i] = Integer.parseInt(bits);
		}

		return out;
	}
	
	// Implementation of the NIST Frequency (Monobit) Test
	// Implemented by Aleks
	// Takes the bit string and a confidence/ significance value
	public static boolean frequencyNIST(String bits, double confidence) {
		System.out.println("NIST Frequency Test:");
		int length = bits.length();
		int sum = 0;
		
		int ones = 0;
		int zeroes = 0;
		
		for (int i = 0; i < length; i++) {
			sum += 2 * Character.getNumericValue(bits.charAt(i)) - 1;
			if (Character.getNumericValue(bits.charAt(i)) == 0) {
				zeroes++;
			}
			else {
				ones++;
			}
		}
		System.out.println("Sum: " + sum);
		System.out.println("Zeroes: " + zeroes);
		System.out.println("Ones: " + ones);
		
		double S = Math.abs(sum)/Math.sqrt(length);
		
		System.out.println("S: " + S);
		
		double pvalue = Erf.erfc(S/Math.sqrt(2));
		
		if (pvalue < confidence) {
			System.out.println("P-value: " + pvalue);
			System.out.println("Null hypothesis rejected (Not random).");
			return false;
		}
		else {
			System.out.println("P-value: " + pvalue);
			System.out.println("Null hypothesis accepted (Sufficiently random).");
			return true;
		}
	}
	
	public static boolean runsNIST(String bits, double confidence) {
		System.out.println("NIST Runs Test:");
		int length = bits.length();
		int V;
		int numOnes = 0;
		
		for (int i = 0; i < length; i++) {
			if (Character.getNumericValue(bits.charAt(i)) == 1) {
				numOnes++;
			}
		}
		
		double pi = numOnes/length;
		
		if (Math.abs(pi - 0.5) >= 2/Math.sqrt(length)) {
			System.out.println("Does not pass frequency test.");
			return false;
		}
		else {
			V = 1;
			for (int i = 1; i < length; i++) {
				if (Character.getNumericValue(bits.charAt(i)) != Character.getNumericValue(bits.charAt(i-1))) {
					V++;
				}
			}
		}
		
		double pvalue = Erf.erfc(Math.abs(V - 2 * length * pi * (1-pi)) / (2 * pi * (1-pi) * Math.sqrt(2*length)));
		
		if (pvalue < confidence) {
			System.out.println("P-value: " + pvalue);
			System.out.println("Null hypothesis rejected (Not random).");
			return false;
		}
		else {
			System.out.println("P-value: " + pvalue);
			System.out.println("Null hypothesis accepted (Sufficiently random).");
			return true;
		}
	}
	
	// Based on https://github.com/stamfest/randomtests/blob/master/src/main/java/net/stamfest/randomtests/nist/DiscreteFourierTransform.java
	public static boolean fourierNIST(String bits, double confidence) {
		System.out.println("NIST Discrete Fourier Transform Test:");
		int length = bits.length();
		double d;
		
		double X[] = new double[length];
		
		for (int i = 0; i < length; i++) {
            X[i] = 2 * (int) Character.getNumericValue(bits.charAt(i)) - 1;
        }
		
		DoubleFFT_1D fft = new DoubleFFT_1D(length);
		fft.realForward(X);
		
		double M[] = new double[length / 2 + 1]; // Not sure if that "+ 1" needs to be there.
		
		M[0] = Math.sqrt(X[0] * X[0]);
		M[length/2] = Math.sqrt(X[1] * X[1]);
		
		for (int i = 0; i < length / 2 - 1; i++) {
            M[i + 1] = Math.hypot(X[2 * i + 2], X[2 * i + 3]);
        }
		
		int count = 0;
        double upperBound = Math.sqrt(Math.log(1/0.05) * length);
        for (int i = 0; i < length / 2; i++) {
            if (M[i] < upperBound) {
                count++;
            }
        }
        
        double N1 = count;
        double N0 = 0.95 * length / 2.0;
        
        d = (N1 - N0) / Math.sqrt(length * 0.95 * 0.05 / 4);
        
        double pvalue = Erf.erfc(Math.abs(d) / Math.sqrt(2));
        
        if (pvalue < confidence) {
			System.out.println("P-value: " + pvalue);
			System.out.println("Null hypothesis rejected (Not random).");
			return false;
		}
		else {
			System.out.println("P-value: " + pvalue);
			System.out.println("Null hypothesis accepted (Sufficiently random).");
			return true;
		}
	}
}
