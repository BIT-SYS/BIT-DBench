//Eamon McCarron
//COMP 2631, Assignment 1
//Feb. 17, 2020

import java.io.*;
import java.util.Stack;

public class BitmapHacker{
	private static final int BYTES_PER_PIXEL = 3;
	private static final int HIDING_ROOM = 2; //The number of least significant figures which can be changed without visibly altering an image.
	private static final int BMP_HEADER_SIZE_OFFSET = 0x000A; //The location of the header size value in the header of a bitmap file
	private static final int BMP_WIDTH_OFFSET = 0x0012;
	private static final int BMP_HEIGHT_OFFSET = 0x0016;
	private static final int BMP_DATA_OFFSET = 0x0036; //Where the pixel data starts in a bitmap file
	private static final int BITS_PER_PI_OFFSET = 0x001C;
	private static final int DWORD_BYTES = 4;  //The number of bytes in a dword
	private static final int BLUR_SIZE = 3; //The size of the blur regions in the blur method.  Must be an odd number.
	private static final float ENHANCE_FACTOR = 1.3f;

	private int trailingZeros;  // the number of trailing zeros on each line
	private int headerSize;
	private int[] header;

	private int width;
	private int height;
	private Pixel[][] pixels;

	public static final String RED = "red";
	public static final String BLUE = "blue";
	public static final String GREEN = "green";



	/**
	 * Reads and stores the relevant information from a bitmap's header, then stores the pixel data into a 2D array.
	 * @param file A bitmap image with 24-bit color depth.
	 * @throws IOException When given an invalid file reference
	 */
	public BitmapHacker(File file) throws IOException{
		if(file == null)
			throw new IllegalArgumentException("Attempted to create BitmapHacker with null file reference.");
		if(!file.isFile())
			throw new IllegalArgumentException("Attempted to create BitmapHacker with file which does not refer to file on disk");

		RandomAccessFile reader = new RandomAccessFile(file, "r");

		//First check that bitmap has 24-bit color depth (ie 3 bytes per pixel):
		reader.seek(BITS_PER_PI_OFFSET);
		int colorDepth = reader.read() + reader.read()*256; //reading a single word in little endian.
		if(colorDepth != BYTES_PER_PIXEL * 8){
			String err = String.format("Attempted to read bitmap with %d-bit color depth.  Can only read bitmap of %d-bit color depth.", colorDepth, BYTES_PER_PIXEL * 8);
			throw new IllegalArgumentException(err);
		}

		reader.seek(BMP_HEADER_SIZE_OFFSET);
		headerSize = 0;
		for(int i = 0; i < DWORD_BYTES; i++) {
			headerSize += reader.read()*Math.pow(256, i);
		}
		header = new int[headerSize];

		reader.seek(0);
		for(int i = 0; i < headerSize; i++) {
			header[i] = reader.read();
		}

		reader.seek(BMP_WIDTH_OFFSET);
		width = 0;
		for(int i = 0; i < DWORD_BYTES; i++) {
			width += reader.read()*Math.pow(256, i);
		}

		trailingZeros = (4 - (width*BYTES_PER_PIXEL) % 4);
		if(trailingZeros == 4)
			trailingZeros = 0;
		reader.seek(BMP_HEIGHT_OFFSET);
		height = 0;
		for(int i = 0; i < DWORD_BYTES; i++) {
			height += reader.read()*Math.pow(256, i);
		}
		pixels = new Pixel[height][width];

		reader.seek(BMP_DATA_OFFSET);
		for(int i = height - 1; i >= 0; i--){
			for(int j = 0; j < width; j++){
				int B = reader.read();
				int G = reader.read();
				int R = reader.read();
				pixels[i][j] = new Pixel(R,G,B);
			}
			for(int n = 0; n < trailingZeros; n++){
				reader.readByte();  //Skip the trailing zeros since they aren't actually rendered and storing them would be cumbersome
			}
		}
		reader.close();
	}

	/**
	 * Attempts to write the currently stored image to a file.
	 * @param img A file on disk to write the stored image to.
	 * @throws IOException When given an invalid file reference.
	 */
	public void writeImageToFile(File img) throws IOException{
		if(img == null)
			throw new IllegalArgumentException("Attempted to write image to null file reference.");
		RandomAccessFile writer = new RandomAccessFile(img, "rw");
		writer.seek(0);
		for(int i = 0; i < headerSize; i++){
			writer.write(header[i]);
		}
		for(int i = height - 1; i >= 0; i--){
			for(int j = 0; j < width; j++) {
				writer.write(pixels[i][j].getBlue());
				writer.write(pixels[i][j].getGreen());
				writer.write(pixels[i][j].getRed());
			}
			for(int n = 0; n < trailingZeros; n++) {  //At the end of every row, write the trailing zeros.
				writer.writeByte(0);
			}
		}
		writer.close();
	}

	/**
	 * @return the height in pixels of the image
	 */
	public int getHeight() {
		return height;
	}

	/**
	 * sets the height of the image in pixels.
	 * @param height the height in pixels of the image
	 */
	public void setHeight(int height) {
		this.height = height;
	}

	/**
	 * returns the height of the image in pixels
	 * @return the width in pixels of the image
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * sets the width of the image in pixels
	 * @param width the width in pixels of the image
	 */
	public void setWidth(int width) {
		this.width = width;
	}

	/**
	 * Flips the currently stored image about its horizontal axis.
	 */
	public void flip(){
		Stack<Pixel[]> rows = new Stack<>();
		for(Pixel[] row : pixels)
			rows.push(row);
		for(int i = 0; i < height; i++)
			pixels[i] = rows.pop();
	}

	/**
	 * Blurs the currently stored image by averaging RGB values of pixels in 3x3 regions.
	 */
	public void blur(){
		Pixel[][] blurred = new Pixel[height][width];
		for(int i = 0; i < height; i++){
			for(int j = 0; j < width; j++){
				Pixel[] blurRegion;
				int k = 0;  //Tracks index of blurRegion
					blurRegion = new Pixel[BLUR_SIZE * BLUR_SIZE];
					for (int x = -BLUR_SIZE/2; x <= BLUR_SIZE/2; x++) { //Selects pixels left and right of current
						for (int y = -BLUR_SIZE/2; y <= BLUR_SIZE/2; y++) { //Selects pixels above and below current
							int row = i + y;
							int col = j + x;
							if(row < 0 || col < 0 || row == height || col == width) {
								continue;
							}
							blurRegion[k++] = pixels[row][col];
						}
					}
					int r = 0, g = 0, b = 0;
					for(Pixel pi : blurRegion){
						if(pi == null)
							continue;
						r += pi.getRed();
						g += pi.getGreen();
						b += pi.getBlue();
					}
					r /= k;
					g /= k;
					b /= k;
					blurred[i][j] = new Pixel(r,g,b);
			}
		}
		pixels = blurred;
	}

	/**
	 * Enhances a color of the currently stored image by multiplying the R, G, or B value of each pixel by 1.3.
	 * @param color The color to enhance, either "red", "green" or "blue".  Use class constants to specify.;
	 */
	public void enhance(String color){
		if(color.equals(RED)){
			for(Pixel[] row : pixels){
				for(Pixel pi : row){
					int red = (int)(pi.getRed()*ENHANCE_FACTOR);
					if(red > 255)
						red = 255;
					pi.setRed(red);
				}
			}
		} else if(color.equals(BLUE)){
			for(Pixel[] row : pixels){
				for(Pixel pi : row){
					int blue = (int)(pi.getBlue()*ENHANCE_FACTOR);
					if(blue > 255)
						blue = 255;
					pi.setBlue(blue);
				}
			}
		} else if(color.equals(GREEN)){
			for(Pixel[] row : pixels){
				for(Pixel pi : row){
					int green = (int)(pi.getGreen()*ENHANCE_FACTOR);
					if(green > 255)
						green = 255;
					pi.setGreen(green);
				}
			}
		} else {
			throw new IllegalArgumentException("Attempted to enhance color that is not 'red', 'green', or 'blue'");
		}
	}

	/**
	 * Attempts to hide a file by modifying the 2 least significant bits of the R,G, and B value of each pixel
	 * @param file The file to hide
	 * @return true if successful, false otherwise
	 * @throws IOException when given an invalid file reference
	 */
	public boolean hide(File file) throws IOException{
		if(file == null){
			throw new IllegalArgumentException("Attempted to hide a null file");
		} else if (!file.isFile()){
			throw new IllegalArgumentException("Attempted to hide a file which does not refer to a file on disk");
		}

		int size = (int)file.length();
		if(size > width*height*HIDING_ROOM)
			return false;

		int[] bytes = new int[size + Integer.BYTES];
		System.out.printf("Attempting to hide file of length:  %d\n", size);

		//Convert size to 32-bit binary string
		StringBuilder sSize = new StringBuilder(Integer.SIZE);
		sSize.append(Integer.toBinaryString(size));
		while(sSize.length() < Integer.SIZE){
			sSize.insert(0, "0");
		}

		//Write size in little endian format to first 4 bytes:
		for(int i = 0; i < Integer.BYTES; i++){
			String byte_ = sSize.substring(i*Byte.SIZE, (i + 1)*Byte.SIZE);
			bytes[Integer.BYTES - 1 - i] = Integer.parseInt(byte_, 2); //Little endian, so store bytes in reverse order
		}

		//Read the bytes of the file into an array
		RandomAccessFile reader = new RandomAccessFile(file, "rw");
		for(int i = Integer.BYTES; i < size; i++){
			bytes[i] = reader.readUnsignedByte();
		}

		int RGB = 0;  //tracks which color we are currently processing
		int iPix = 0;  //tracks which pixel we are currently processing
		Pixel pi = pixels[0][0];  //A reference to the current pixel we are processing
		for(int i = 0; i < size ; i++){
			StringBuilder curByte_ = new StringBuilder(Byte.SIZE);
			curByte_.append(Integer.toBinaryString(bytes[i]));
			while(curByte_.length() < Byte.SIZE){
				curByte_.insert(0,"0");
			}
			String curByte = curByte_.toString();
			int cur2Bits = 0;  //tracks which 2-bit piece of curByte we are currently processing (a half nibble?)
			for(int c = 0; c < 8 / HIDING_ROOM; c++) {  //To hide one byte, need to process 4 'colors'
				String bits = curByte.substring(cur2Bits*2 , cur2Bits*2 + 2);
				if(RGB == 0){
					RGB++;
					int red = pi.getRed();
					String redData = Integer.toBinaryString(red);
					if(redData.length() > 1)
						redData = redData.substring(0, redData.length() - HIDING_ROOM) + bits;
					else
						redData = bits;

					pi.setRed(Integer.parseInt(redData, 2));
				} else if (RGB == 1){
					RGB++;
					int green = pi.getGreen();
					String greenData = Integer.toBinaryString(green);

					if(greenData.length() > 1)
						greenData = greenData.substring(greenData.length() - HIDING_ROOM) + bits;
					else
						greenData = bits;

					pi.setGreen(Integer.parseInt(greenData, 2));
				} else if (RGB == 2){
					int blue = pi.getBlue();
					String blueData = Integer.toBinaryString(blue);
					if(blueData.length() > 1)
						blueData = blueData.substring(blueData.length() - HIDING_ROOM) + bits;
					else
						blueData = bits;

					pi.setBlue(Integer.parseInt(blueData, 2));

					RGB = 0;  //Reset back to red
					iPix++;
					pi = pixels[iPix / width][iPix % width];
				}
				cur2Bits++;
			}

		}
		return true;
	}

	/**
	 * Attempts to construct a file from the least 2 significant bits of each color of each pixel.
	 * @param file The file to write to.
	 * @return true if successful, false if there is insufficient space to hide the file.
	 * @throws IOException when given an invalid file reference.
	 */
	public boolean unhide(File file) throws IOException{
		if(file == null) {
			throw new IllegalArgumentException("Attempted to write to a null file while unhiding");
		}
		int length = getHiddenFileLength();
		if(length == -1 || length > width*height*HIDING_ROOM - Integer.BYTES*8)
			return false;
		System.out.printf("Attempting to unhide file of length: %d\n", length);
		int[] hiddenBytes = new int[length];

		int RGB = 1;  //The current color we are processing.  Start at one since the hidden file length ended on a red;
		int iPix = 5; //Index of the current pixel we are processing.
		Pixel pixel = pixels[0][5]; //Reference to the current pixel we are processing.  Pick up where hidden file length left off.
	    for(int i = 0; i < length; i++){
			StringBuilder byte_ = new StringBuilder(Byte.SIZE);
			for(int c = 0; c < 8 / HIDING_ROOM; c++) {   //Process 4 colors for every byte of data
				String hiddenBits = "";
				if(RGB == 0){
					String red = Integer.toBinaryString(pixel.getRed());
					RGB++;

					if(red.length() != 1)
						hiddenBits = red.substring(red.length() - HIDING_ROOM);
					else
						hiddenBits = "0" + red;


				} else if (RGB == 1){
					RGB++;
					String green = Integer.toBinaryString(pixel.getGreen());

					if(green.length() != 1)
						hiddenBits = green.substring(green.length() - HIDING_ROOM);
					else
						hiddenBits = "0" + green;

				} else if (RGB == 2){
					RGB = 0;

					String blue = Integer.toBinaryString(pixel.getBlue());
					if(blue.length() != 1)
						hiddenBits = blue.substring(blue.length() - HIDING_ROOM);
					else
						hiddenBits = "0" + blue;
					iPix++;
					pixel = pixels[iPix / width][iPix % width];
				}
				byte_.append(hiddenBits);
			}

			hiddenBytes[i] = Integer.parseInt(byte_.toString(), 2);
		}
	    RandomAccessFile writer = new RandomAccessFile(file, "rw");
	    for(int byte_ : hiddenBytes){
	    	writer.writeByte(byte_);
		}
	    writer.close();
		return true;
    }

	/**
	 * Extracts the length from the least significant bits of the first 16 RGB values (in the form a 32-bit integer).
	 * @return The length of a hidden file, assuming the bitmap has a file hidden in it.
	 */
    private int getHiddenFileLength(){
		if(width * height < Integer.SIZE / (BYTES_PER_PIXEL * HIDING_ROOM))
			return -1; //Then there is not enough room to hide a 32-bit integer

		StringBuilder sLength = new StringBuilder(Integer.SIZE);
		//To find length, need to process 32 bits, which is the first 5 + 1/3 pixels.
		for(int i = 0; i < 5; i++){  //Process 5 pixels
			Pixel pixel = pixels[i / width][i % width];

			String red = Integer.toBinaryString((pixel.getRed()));
			String redData;
			if(red.length() != 1) {
				redData = red.substring(red.length() - HIDING_ROOM);
			} else {
				redData = "0" + red;
			}

			String blueData;
			String blue = Integer.toBinaryString(pixel.getBlue());
			if(blue.length() != 1) {
				blueData = blue.substring(blue.length() - HIDING_ROOM);
			} else {
				blueData = "0" + blue;
			}

			String greenData;
			String green = Integer.toBinaryString(pixel.getGreen());
			if(green.length() != 1) {
				greenData = green.substring(green.length() - HIDING_ROOM);
			} else {
				greenData = "0" + green;
			}

			sLength.append(redData).append(greenData).append(blueData);
		}

		//Process the last pixel outside the loop
		Pixel pi = pixels[0][5];
		//This is the last two bits of the file length
		String red = Integer.toBinaryString((pi.getRed()));
		String redData = red.substring(red.length() - HIDING_ROOM);
		sLength.append(redData);

		int length = 0;
		for(int i = 0; i < Integer.BYTES; i++){
			String bin = sLength.substring(i*8, i*8 + 8);
			length += Integer.parseInt(bin, 2)*Math.pow(256, i);
		}

		return length;
	}
}
