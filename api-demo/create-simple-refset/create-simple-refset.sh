#!/bin/bash
#
# Command line statements which use the API to create a simple refset
#

# Stop on error
set -e;

# Declare parameters
api=http://localhost:8080/api/v1
#api=http://local.ihtsdotools.org/api/v1
#api="http://uat-release.ihtsdotools.org/api/v1"
#api="http://dev-release.ihtsdotools.org/api/v1"
buildId="1_20140731_international_release_build"
packageId="snomed_release_package"

curlFlags="isS"
# i - Show response header
# s - quiet
# S - show errors

# Constants
readmeHeader="readme-header.txt"
manifest="manifest.xml"

echo
echo "Target API URL is '${api}'"
echo "Target Build ID is '${buildId}'"
echo "Target Package ID is '${packageId}'"
echo

# Login
echo "Login and record authorisation token."
curl -${curlFlags} -F username=manager -F password=test123 $api/login > tmp/login-response.txt
token=`cat tmp/login-response.txt | grep "Token" | sed 's/.*: "\([^"]*\)".*/\1/g'`
echo "Token is '${token}'"
commonParams="-${curlFlags} -u ${token}:"
echo

echo "Set Readme Header"
readmeHeaderContents=`cat ${readmeHeader} | python -c 'import json,sys; print json.dumps(sys.stdin.read())' | sed -e 's/^.\(.*\).$/\1/'`
echo "readmeHeaderContents: ${readmeHeaderContents}"
curl ${commonParams} -X PATCH -H 'Content-Type:application/json' --data-binary "{ \"readmeHeader\" : \"${readmeHeaderContents}\" }" $api/builds/${buildId}/packages/${packageId}  | grep HTTP
echo

echo "Upload Manifset"
curl ${commonParams} -F "file=@${manifest}" $api/builds/${buildId}/packages/${packageId}/manifest | grep HTTP
echo

echo "Upload Input Files:"
for file in `ls input-files`;
do
	echo "Upload Input File ${file}"
	curl ${commonParams} -F "file=@input-files/${file}" $api/builds/${buildId}/packages/${packageId}/inputfiles | grep HTTP
done
echo

echo "Set effectiveTime"
curl ${commonParams} -X PATCH -H 'Content-Type:application/json' --data-binary '{ "effectiveTime" : "2014-01-31" }' $api/builds/${buildId}  | grep HTTP
echo

echo "Create Execution"
curl ${commonParams} -X POST $api/builds/${buildId}/executions > tmp/execution-response.txt
executionId=`cat tmp/execution-response.txt | grep "id" | sed 's/.*: "\([^"]*\).*".*/\1/g'`
echo "Execution ID is '${executionId}'"
echo

echo "Trigger Execution"
curl ${commonParams} -X POST $api/builds/${buildId}/executions/${executionId}/trigger  | grep HTTP
echo
