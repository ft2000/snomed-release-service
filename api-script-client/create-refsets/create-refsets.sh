#!/bin/bash

# Stop on error
set -e;

#
# Command line statements which use the API to create a simple refset
# Expects to be called from one of the run_*.sh scripts.
#

# Declare common parameters
api=http://localhost:8080/api/v1
#api="https://local.ihtsdotools.org/api/v1"
#api="https://uat-release.ihtsdotools.org/api/v1"
#api="http://dev-release.ihtsdotools.org/api/v1"
#api="http://release.ihtsdotools.org/api/v1"

rcId=international
extId=snomed_ct_international_edition
prodId=snomed_ct_release
buildId="1_20140731_international_release_build"
packageId="snomed_release_package"
readmeHeader="readme-header.txt"

#Set curl verbocity
curlFlags="isS"
# i - Show response header
# s - quiet
# S - show errors

ensureCorrectResponse() {
	while read response 
	do
		httpResponseCode=`echo $response | grep "HTTP" | awk '{print $2}'`
		echo " Response received: $response "
		if [ "${httpResponseCode:0:1}" != "2" ] && [ "${httpResponseCode:0:1}" != "1" ]
		then
			echo "Failure detected with non-2xx HTTP response code received.  Script halted"
			exit -1
		fi
	done
	echo
}

if [ -z "$calling_program" ]
then
	echo "Usage"
	echo "------"
	echo "Please call this script from one of the 'run' scripts."
	echo
	exit -1	
fi

echo
echo "Target API URL is '${api}'"
echo "Target Build ID is '${buildId}'"
echo "Target Package ID is '${packageId}'"
echo

# Login
echo "Login and record authorisation token."
curl -${curlFlags} -F username=manager -F password=test123 ${api}/login | tee tmp/login-response.txt | grep HTTP | ensureCorrectResponse
token=`cat tmp/login-response.txt | grep "Token" | sed 's/.*: "\([^"]*\)".*/\1/g'`
echo "Authorisation Token is '${token}'"
#Ensure we have a valid token before proceeding
if [ -z "${token}" ]
then
	echo "Cannot proceed further if we haven't logged in!"
	exit -1
fi
commonParamsSilent="-s --retry 0 -u ${token}:"
commonParams="-${curlFlags} --retry 0 -u ${token}:"

echo

#Only need a readme header for a first time run
if [ "${isFirstTime}"=true ]
then
	echo "Set Readme Header"
	readmeHeaderContents=`cat ${readmeHeader} | python -c 'import json,sys; print json.dumps(sys.stdin.read())' | sed -e 's/^.\(.*\).$/\1/'`
	#echo "readmeHeaderContents: ${readmeHeaderContents}"
	curl ${commonParams} -X PATCH -H 'Content-Type:application/json' --data-binary "{ \"readmeHeader\" : \"${readmeHeaderContents}\" }" ${api}/builds/${buildId}/packages/${packageId}  | grep HTTP | ensureCorrectResponse
fi

manifestPath="manifest-files/${manifestFileName}"
echo "Upload Manifest from ${manifestPath}"
curl ${commonParams} --write-out \\n%{http_code} -F "file=@${manifestPath}" ${api}/builds/${buildId}/packages/${packageId}/manifest  | grep HTTP | ensureCorrectResponse

#If we've done a different release before, then we need to delete the input files from the last run!
#Not checking the return code from this call, doesn't matter if the files aren't there
echo "Delete previous delta Input Files "
curl ${commonParams} -X DELETE ${api}/builds/${buildId}/packages/${packageId}/inputfiles/*.txt | grep HTTP | ensureCorrectResponse


inputFilesPath="input-files/${executionName}"
echo "Upload Input Files from ${inputFilesPath}:"
for file in `ls ${inputFilesPath}`;
do
	echo "Upload Input File ${file}"
	curl ${commonParams} -F "file=@${inputFilesPath}/${file}" ${api}/builds/${buildId}/packages/${packageId}/inputfiles | grep HTTP | ensureCorrectResponse
done

echo "Set effectiveTime to ${effectiveDate}"
curl ${commonParams} -X PATCH -H 'Content-Type:application/json' --data-binary "{ \"effectiveTime\" : \"${effectiveDate}\" }"  ${api}/builds/${buildId}  | grep HTTP | ensureCorrectResponse

#Set the first time release flag, and if a subsequent release, recover the previously published package and set that
if ${isFirstTime}
then
	echo "Set first time flag to ${firstTimeStr}"
	curl ${commonParams} -X PATCH -H 'Content-Type:application/json' --data-binary "{ \"firstTimeRelease\" : \"${firstTimeStr}\"  }" ${api}/builds/${buildId}/packages/${packageId}  | grep HTTP | ensureCorrectResponse
else
	echo "Recover previously published release"
	# eg /centers/international/extensions/snomed_ct_international_edition/products/snomed_ct_release/published
	curl ${commonParams} ${api}/centers/${rcId}/extensions/${extId}/products/${prodId}/published > tmp/published-response.txt
	publishedName=`cat tmp/published-response.txt | grep "publishedPackages" | sed 's/.*: \[ "\([^"]*\).*".*/\1/g'`
	echo "Previously published package detected as ${publishedName}"
	
	echo "Set first time flag to ${firstTimeStr} and previous published package to ${publishedName}"
	updateJSON="{ \"firstTimeRelease\" : \"${firstTimeStr}\", \"previousPublishedPackage\" : \"${publishedName}\" }"
	curl ${commonParams} -X PATCH -H 'Content-Type:application/json' --data-binary "$updateJSON" ${api}/builds/${buildId}/packages/${packageId}  | grep HTTP | ensureCorrectResponse
fi
 

echo "Create Execution"
curl ${commonParams} -X POST ${api}/builds/${buildId}/executions | tee tmp/execution-response.txt | grep HTTP | ensureCorrectResponse
executionId=`cat tmp/execution-response.txt | grep "id" | sed 's/.*: "\([^"]*\).*".*/\1/g'`
echo "Execution ID is '${executionId}'"

echo "Trigger Execution"
curl ${commonParams} -X POST ${api}/builds/${buildId}/executions/${executionId}/trigger  | tee tmp/trigger-response.txt | grep HTTP | ensureCorrectResponse
triggerSuccess=`cat tmp/trigger-response.txt | grep pass`
if [ -z "${triggerSuccess}" ]
then
	echo "Failed to successfully process any packages"
	echo
	cat tmp/trigger-response.txt
	echo
	echo "Script Halted"
	sleep 1s
	exit -1
fi

echo "Publish the package"
curl ${commonParams} ${api}/builds/${buildId}/executions/${executionId}/output/publish  | grep HTTP | ensureCorrectResponse

echo "List the output files"
downloadUrlRoot=${api}/builds/${buildId}/executions/${executionId}/packages/${packageId}/outputfiles
localDownloadDirectory=`echo "tmp/output_${executionName}_${executionId}" | sed s/://g`
curl ${commonParams} ${downloadUrlRoot} | tee tmp/output-file-listing.txt | grep HTTP | ensureCorrectResponse


mkdir ${localDownloadDirectory}
downloadFile() {
	read fileName
	echo "Downloading file to: ${localDownloadDirectory}/${fileName}"
	#Using curl as the MAC doesn't have wget loaded by default
	#wget -P ${localDownloadDirectory} ${downloadUrlRoot}/${fileName}
	curl ${commonParamsSilent} ${downloadUrlRoot}/${fileName} -o "${localDownloadDirectory}/${fileName}"
}
cat tmp/output-file-listing.txt | grep id | while read line ; do echo  $line | sed 's/.*: "\([^"]*\).*".*/\1/g' | downloadFile; done

echo
echo "Process Complete"
echo
