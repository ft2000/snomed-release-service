package org.ihtsdo.buildcloud.dao;

import org.ihtsdo.buildcloud.entity.Package;

public interface PackageDAO extends EntityDAO<Package> {
	Package find(Long buildId, String packageBusinessKey, String authenticatedId);
}