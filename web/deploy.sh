#!/bin/bash
# Stop on error
set -e

## Uncomment next line to disable script
#exit

echo "Bringing snomed-release-service up to date."
echo "Running as user `whoami`"
echo

# Pull updates. (Project should be cloned over ssh using an ssh-key without a password to allow unattended git pull.)
cd ../.source/snomed-release-service

echo "Pulling updates..."
git pull --verbose 2>&1
echo

if ( git log -n1 | head -n1 | diff - deployed-commit.txt )
then
	echo 'No updates, quiting.'
else
	echo 'There are updates, building...'
	echo

	# Add/overwrite version files
	echo -e "`date`\n-----Latest Commit-----\n`git log -1`" > api/src/main/webapp/version.txt
	echo -e "`date`\n-----Latest Commit-----\n`git log -1`" > web/version.txt

	echo "Maven build..."
	mvn clean install -DskipTests=true
	echo

	echo "Deploy API..."
	cp api/target/api.war ../../.webapps/
	echo

	echo "Deploy static site..."
	rm -rf ../../snomed-release-service-web/*
	cp -r web/* ../../snomed-release-service-web/
	echo

	echo "Restart Builder process"
	sh builder/scripts/restart-builder.sh

	echo "Recording deployed commit..."
	git log -n1 | head -n1 > deployed-commit.txt
	echo

	echo "Done"

fi
