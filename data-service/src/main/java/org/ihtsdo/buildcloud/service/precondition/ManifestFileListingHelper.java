package org.ihtsdo.buildcloud.service.precondition;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.buildcloud.manifest.FileType;
import org.ihtsdo.buildcloud.manifest.FolderType;
import org.ihtsdo.buildcloud.manifest.ListingType;

public class ManifestFileListingHelper {

	public static List<String> listAllFiles(ListingType listingType) {
		FolderType rootFolder = listingType.getFolder();
		List<String> result = new ArrayList<>();
		getFilesFromCurrentAndSubFolders(rootFolder, result);
		return result;
	}

	private static void getFilesFromCurrentAndSubFolders(FolderType folder, List<String> filesList) {
		if (folder != null) {
			if (folder.getFile() != null) {
				for (FileType fileType : folder.getFile()) {

					filesList.add(fileType.getName());
				}
			}
			if (folder.getFolder() != null) {
				for (FolderType subFolder : folder.getFolder()) {
					getFilesFromCurrentAndSubFolders(subFolder, filesList);
				}
			}
		}
	}
}
