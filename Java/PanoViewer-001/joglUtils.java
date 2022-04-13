/*
 *
 */
package PanoViewer.Utils;

import static PanoViewer.Settings.invertImage;
import static PanoViewer.Utils.IOUtils.getFileFromResourceAsStream;
import static PanoViewer.Utils.imageutils.getFlipedImage;
import static com.jogamp.opengl.GL.GL_NO_ERROR;
import static com.jogamp.opengl.GL2ES2.GL_COMPILE_STATUS;
import static com.jogamp.opengl.GL2ES2.GL_FRAGMENT_SHADER;
import static com.jogamp.opengl.GL2ES2.GL_INFO_LOG_LENGTH;
import static com.jogamp.opengl.GL2ES2.GL_LINK_STATUS;
import static com.jogamp.opengl.GL2ES2.GL_VERTEX_SHADER;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import java.awt.image.BufferedImage;
import java.util.Scanner;
import java.util.Vector;

/**
 *
 * @author kshan
 */
public class joglUtils {

  public static Texture getTexture(TextureData textureData) {
    GL3 gl = (GL3) GLContext.getCurrentGL();
    return new Texture(gl, textureData);
  }

  public static TextureData getTextureData(BufferedImage image) {
    TextureData textureData = AWTTextureIO.newTextureData(GLProfile.getMaxProgrammable(true),
            invertImage() ? getFlipedImage(image) : image, true);
    return textureData;
  }

  public static String[] readShaderSource(String filename) {

    Vector<String> lines = new Vector<String>();
    Scanner sc;
    sc = new Scanner(getFileFromResourceAsStream(filename));
    while (sc.hasNext()) {
      lines.addElement(sc.nextLine());
    }
    String[] program = new String[lines.size()];
    for (int i = 0; i < lines.size(); i++) {
      program[i] = (String) lines.elementAt(i) + "\n";
    }
    return program;
  }

  public static int createShaderProgram(String vFileName, String fFileName) {
    int[] vertCompiled = new int[1];
    int[] fragCompiled = new int[1];
    int[] linked = new int[1];
    GL3 gl = (GL3) GLContext.getCurrentGL();
    String[] vshaderSource = readShaderSource(vFileName);
    String[] fshaderSource = readShaderSource(fFileName);
    int vShader = gl.glCreateShader(GL_VERTEX_SHADER);
    gl.glShaderSource(vShader, vshaderSource.length, vshaderSource, null, 0);
    gl.glCompileShader(vShader);
    checkOpenGLError(); // can use returned boolean
    gl.glGetShaderiv(vShader, GL_COMPILE_STATUS, vertCompiled, 0);
    if (vertCompiled[0] != 1) {
      System.out.println(vFileName + " vertex compilation failed.");
      printShaderLog(vShader);
    }

    int fShader = gl.glCreateShader(GL_FRAGMENT_SHADER);
    gl.glShaderSource(fShader, fshaderSource.length, fshaderSource, null, 0);
    gl.glCompileShader(fShader);
    checkOpenGLError(); // can use returned boolean
    gl.glGetShaderiv(fShader, GL_COMPILE_STATUS, fragCompiled, 0);
    if (fragCompiled[0] != 1) {
      System.out.println(fFileName + " fragment compilation failed.");
      printShaderLog(fShader);
    }

    if ((vertCompiled[0] != 1) || (fragCompiled[0] != 1)) {
      System.out.println("\nCompilation error; return-flags:");
      System.out.println(" vertCompiled = " + vertCompiled[0]
              + "fragCompiled =  " + fragCompiled[0]);
    }

    int vfprogram = gl.glCreateProgram();
    gl.glAttachShader(vfprogram, vShader);
    gl.glAttachShader(vfprogram, fShader);
    gl.glLinkProgram(vfprogram);

    checkOpenGLError();
    gl.glGetProgramiv(vfprogram, GL_LINK_STATUS, linked, 0);
    if (linked[0] != 1) {
      System.out.println("vfprogram linking failed.");
      printProgramLog(vfprogram);
    }

    gl.glDeleteShader(vShader);
    gl.glDeleteShader(fShader);
    return vfprogram;
  }

  static private void printShaderLog(int shader) {
    GL3 gl = (GL3) GLContext.getCurrentGL();
    int[] len = new int[1];
    int[] chWrittn = new int[1];
    byte[] log = null;
    gl.glGetShaderiv(shader, GL_INFO_LOG_LENGTH, len, 0);
    if (len[0] > 0) {
      log = new byte[len[0]];
      gl.glGetShaderInfoLog(shader, len[0], chWrittn, 0, log, 0);
      System.out.println("Shader Info Log: ");
      for (int i = 0; i < log.length; i++) {
        System.out.print((char) log[i]);
      }
    }
  }

  static private void printProgramLog(int program) {
    GL3 gl = (GL3) GLContext.getCurrentGL();
    int[] len = new int[1];
    int[] chWrittn = new int[1];
    byte[] log = null;
    gl.glGetShaderiv(program, GL_INFO_LOG_LENGTH, len, 0);
    if (len[0] > 0) {
      log = new byte[len[0]];
      gl.glGetShaderInfoLog(program, len[0], chWrittn, 0, log, 0);
      System.out.println("Shader Info Log: ");
      for (int i = 0; i < log.length; i++) {
        System.out.print((char) log[i]);
      }
    }
  }

  static boolean checkOpenGLError() {
    GL3 g = (GL3) GLContext.getCurrentGL();
    boolean foundError = false;
    GLU glu = new GLU();
    int glErr = g.glGetError();
    while (glErr != GL_NO_ERROR) {
      System.out.println("glError " + glu.gluErrorString(glErr));
      foundError = true;
      glErr = g.glGetError();
    }
    return foundError;
  }
}
