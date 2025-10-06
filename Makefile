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
BUILDDIR="./build"

JFLAGS = -g -Xlint:unchecked -Xlint:deprecation -extdirs ./external -d $(BUILDDIR)
JFLAGS+= -target 1.8 -source 1.8 -bootclasspath ./external/rt-1.8.jar 

# remove command to clean up
RM = rm -vf

.PHONY:	all build-dir copy-resources org/fairsim/git-version.txt

all:	build-dir copy-resources
	$(JC) $(JFLAGS) org/fairsim/*/*.java

build-dir:
	mkdir -p $(BUILDDIR)

copy-resources: build-dir
	mkdir -p $(BUILDDIR)/org/fairsim/resources
	cp -r org/fairsim/resources/* $(BUILDDIR)/org/fairsim/resources/

linalg: build-dir
	$(JC) $(JFLAGS) org/fairsim/linalg/*.java
utils: build-dir
	$(JC) $(JFLAGS) org/fairsim/utils/*.java
fiji: build-dir
	$(JC) $(JFLAGS) org/fairsim/fiji/*.java
sim_algorithm: build-dir
	$(JC) $(JFLAGS) org/fairsim/sim_algorithm/*.java
sim_gui: build-dir
	$(JC) $(JFLAGS) org/fairsim/sim_gui/*.java


# misc rules
git-version : build-dir
	git rev-parse HEAD > $(BUILDDIR)/org/fairsim/git-version.txt  ; \
	git tag --contains >> $(BUILDDIR)/org/fairsim/git-version.txt ; \
	echo "n/a" >> $(BUILDDIR)/org/fairsim/git-version.txt
	 	

jar:	git-version jtransforms-fork copy-resources
	$(JAR) -cfm fairSIM_plugin_$(shell head -c 10 $(BUILDDIR)/org/fairsim/git-version.txt).jar \
	Manifest.txt \
	-C $(BUILDDIR) org/fairsim \
	plugins.config 

jar-wo-extern: git-version copy-resources
	$(JAR) -cfm fairSIM_woJTransforms_plugin_$(shell head -c 10 $(BUILDDIR)/org/fairsim/git-version.txt).jar \
	Manifest.txt \
	-C $(BUILDDIR) org/fairsim/linalg \
	-C $(BUILDDIR) org/fairsim/utils \
	-C $(BUILDDIR) org/fairsim/fiji \
	-C $(BUILDDIR) org/fairsim/sim_algorithm \
	-C $(BUILDDIR) org/fairsim/sim_gui \
	-C $(BUILDDIR) org/fairsim/git-version.txt \
	-C $(BUILDDIR) org/fairsim/resources \
	plugins.config 


# shorthand for extracting the jtransforms-fork is necessary
jtransforms-fork: build-dir $(BUILDDIR)/org/fairsim/extern/jtransforms/FloatFFT_3D.class

$(BUILDDIR)/org/fairsim/extern/jtransforms/FloatFFT_3D.class:	
	cd $(BUILDDIR) && $(JAR) -xvf ../external/jtransforms_fairSIM_fork.jar org/fairsim/extern/jtransforms 	

clean-jtransforms:
	$(RM) -r $(BUILDDIR)/org/fairsim/extern

# shorthand for generating the doc
doc:	doc/index.html

doc/index.html : $(wildcard org/fairsim/*/*.java) 
	javadoc -d doc/ -classpath ./ -extdirs ${EXTDIR} \
	-subpackages org.fairsim -exclude org.fairsim.extern.jtransforms 

clean : clean-jtransforms
	$(RM) fairSIM_*.jar fairSIM_*.tar.bz2
	$(RM) -r $(BUILDDIR)
	$(RM) -r doc/*
	$(RM) -r target

