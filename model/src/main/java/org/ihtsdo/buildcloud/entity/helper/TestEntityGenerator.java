package org.ihtsdo.buildcloud.entity.helper;

import org.ihtsdo.buildcloud.entity.Build;
import org.ihtsdo.buildcloud.entity.Extension;
import org.ihtsdo.buildcloud.entity.InputFile;
import org.ihtsdo.buildcloud.entity.Package;
import org.ihtsdo.buildcloud.entity.Product;
import org.ihtsdo.buildcloud.entity.ReleaseCentre;

public class TestEntityGenerator {

	public static final String [] releaseCentreNames = {"International"};
	
	public static final String [] extensionNames = {"SNOMED CT International Edition"};	
	
	public static final String [] productNames = {	"SNOMED CT International Edition",
													"SNOMED CT Spanish Edition"};		
	
	public static final String [] buildNames = { "20130731 International Release",
												 "20140131 International Release - Biannual",
												 "20140131 International Release - Nightly",
												 "20140731 International Release - Biannual"};
	
	public static final String [] packageNames = {	"RF2 Release",
													"RF1CompatibilityPackage",
													"RF2toRF1Conversion"};		
	
	public static final String [] inputFileNames = { "concepts.rf2" };
	
	protected ReleaseCentre createTestReleaseCentre() {
		
		ReleaseCentre internationalReleaseCentre = new ReleaseCentre(releaseCentreNames[0]);
		Extension extension = new Extension(extensionNames[0]);
		internationalReleaseCentre.addExtension(extension);

		addProductsToExtension(extension);	
		return internationalReleaseCentre;
	}	
	
	protected void addProductsToExtension (Extension extension) {
		for (String productName : productNames) {
			Product product = new Product (productName);
			extension.addProduct(product);
			for (String buildName : buildNames){
				Build build = new Build(buildName);
				product.addBuild(build);
				addPackagesToBuild(build);
			}
		}
	}
	
	protected void addPackagesToBuild (Build build) {
		for (String packageName: packageNames) {
			Package pkg = new Package(packageName);
			build.addPackage(pkg);
			if (packageName.equals(packageNames[0])){
				for (String inputFileName : inputFileNames){
					InputFile inputFile = new InputFile(inputFileName);
					pkg.addInputFile(inputFile);
				}
			}
		}		
	}	
	
}