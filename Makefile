#
# fairSIM make file
#
# To work, either 'java6' has to point to a java 
# compiler (vers. 1.6), or change the line below to
# 'java' instead of 'java6'

JC = javac
JAR = jar

# Options for the java compiler
EXTDIR="./external"

JFLAGS = -g -Xlint:unchecked -Xlint:deprecation -extdirs ./external -d ./
JFLAGS+= -target 1.6 -source 1.6 -bootclasspath ./external/rt-1.6.jar
#JFLAGS+= -release 6


# remove command to clean up
RM = rm -vf

.PHONY:	all org/fairsim/git-version.txt

all:	
	$(JC) $(JFLAGS) org/fairsim/*/*.java

linalg:
	$(JC) $(JFLAGS) org/fairsim/linalg/*.java
utils:
	$(JC) $(JFLAGS) org/fairsim/utils/*.java
fiji:
	$(JC) $(JFLAGS) org/fairsim/fiji/*.java
sim_algorithm:
	$(JC) $(JFLAGS) org/fairsim/sim_algorithm/*.java
sim_gui:
	$(JC) $(JFLAGS) org/fairsim/sim_gui/*.java


# misc rules
git-version :
	git rev-parse HEAD > org/fairsim/git-version.txt  ; \
	git tag --contains >> org/fairsim/git-version.txt ; \
	echo "n/a" >> org/fairsim/git-version.txt
	 	

jar:	git-version jtransforms-fork
	$(JAR) -cfm fairSIM_plugin_$(shell head -c 10 org/fairsim/git-version.txt).jar \
	Manifest.txt \
	org/fairsim/*/*.class  org/fairsim/extern/*/*.class \
	org/fairsim/git-version.txt \
	org/fairsim/resources/* \
	plugins.config 

jar-wo-extern: git-version
	$(JAR) -cfm fairSIM_woJTransforms_plugin_$(shell head -c 10 org/fairsim/git-version.txt).jar \
	Manifest.txt \
	org/fairsim/*/*.class \
	org/fairsim/git-version.txt \
	org/fairsim/resources/* \
	plugins.config 


# shorthand for extracting the jtransforms-fork is necessary
jtransforms-fork: org/fairsim/extern/jtransforms/FloatFFT_3D.class

org/fairsim/extern/jtransforms/FloatFFT_3D.class:	
	$(JAR) -xvf external/jtransforms_fairSIM_fork.jar org/fairsim/extern/jtransforms 	

clean-jtransforms:
	$(RM) org/fairsim/external
	$(RM) org/fairsim/git-version-jtransforms.txt

# shorthand for generating the doc
doc:	doc/index.html

doc/index.html : $(wildcard org/fairsim/*/*.java) 
	javadoc -d doc/ -classpath ./ -extdirs ${EXTDIR} \
	-subpackages org.fairsim -exclude org.fairsim.extern.jtransforms 

clean : clean-jtransforms
	$(RM) fairSIM_*.jar fairSIM_*.tar.bz2
	$(RM) org/fairsim/*/*.class org/fairsim/git-version.txt
	$(RM) org/fairsim/extern/*/*.class
	$(RM) -r doc/*
	$(RM) -r target

