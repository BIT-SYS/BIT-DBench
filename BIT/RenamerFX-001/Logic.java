/*
 * Copyright Otso Rajala <ojrajala@gmail.com>, 2020
 *
 */

package com.github.otjura.renamerfx.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Common static methods for both GUI and command-line.
 */
public final class Logic {
private static final String EMPTY_STRING = "";

/**
 * Traverses a given file tree, returning array of File objects upon success. Traversing recursively is an option.
 *
 * @param dir Path, assumes it's an existing valid directory.
 * @param recurse TRUE to recurse the directory tree down to root, FALSE to do only dir
 * @return List of File objects
 * @throws IOException in case something goes wrong reading files
 */
public static List<File> collectFiles(Path dir, boolean recurse) throws IOException {
	Predicate<Path> isFile = (p -> p.toFile().isFile());
	List<File> files;
	if (recurse) {
		files = Files.walk(dir, Integer.MAX_VALUE)
			.filter(isFile)
			.map(Path::toFile)
			.collect(Collectors.toList());
	} else {
		files = Files.walk(dir, 1)
			.filter(isFile)
			.map(Path::toFile)
			.collect(Collectors.toList());
	}
	return files;
}

/**
 * Renames File objects provided in an input array. Renaming is done in place, replacing the original file. This is
 * default behaviour in common usual tools such as mv and ren.
 *
 * @param files list of File objects
 * @param replaceWhat String to replace in filenames. Assumes no empty String.
 * @param replaceTo String acting as a replacement. Can be empty for deletion.
 * @param simulate when true doesn't rename anything, but returns new names (dry run)
 * @return List containing string representations of succeeded renames
 */
public static List<StringTuple> renameFiles(List<File> files, String replaceWhat, String replaceTo, boolean simulate) {
	List<StringTuple> renamedFiles = new ArrayList<>();

	for (File file : files) {
		if (file.canRead()) {
			String filename = file.getName();
			String newname = filename.replace(replaceWhat, replaceTo);
			String fullpath = file.getParent() + File.separator;
			String fullnewname = fullpath + newname;
			// Only collect actually renamed files
			if (!filename.equals(newname)) {
				if (!simulate) {
					try {
						// renames files returning success/failure
						if (!file.renameTo(new File(fullnewname))) {
							renamedFiles.add(new StringTuple(
								"ERROR " + filename + " couldn't be renamed!",
								EMPTY_STRING));
						}
					} catch (SecurityException e) {
						e.printStackTrace();
					}
				}
				renamedFiles.add(new StringTuple(filename, newname));
			}
		}
	}
	return renamedFiles;
}

/**
 * Read in files in a directory without renaming them, then return their name in StringTuple.
 *
 * @param files List of File objects.
 * @return List of tuples where each is (fileName, EMPTY_STRING)
 */
public static List<StringTuple> filesAsStringTuples(List<File> files) {
	List<StringTuple> stringTuples = new ArrayList<>(0);
	files.forEach(file -> stringTuples.add(new StringTuple(file.getName(), EMPTY_STRING)));
	return stringTuples;
}

/**
 * Checks that target file object is operable directory.
 *
 * @param dir a File object
 * @return boolean, TRUE on operability FALSE otherwise
 */
public static boolean isValidFolder(File dir) {
	try {
		return dir.exists() && dir.isDirectory();
	} catch (SecurityException e) {
		e.printStackTrace();
		return false;
	}
}

/**
 * Checks that target directory is operable.
 *
 * @param dir String
 * @return boolean, TRUE on operability
 */
public static boolean isValidFolder(String dir) {
	return isValidFolder(new File(dir));
}
}
