#
# fairSIM make file
#
# To work, either 'java6' has to point to a java 
# compiler (vers. 1.6), or change the line below to
# 'java' instead of 'java6'

JC = javac6
JAR = jar

# Options for the java compiler
EXTDIR="./external"

JFLAGS = -g -Xlint:unchecked -Xlint:deprecation -extdirs ${EXTDIR} -d ./
#JFLAGS = -g -Xlint:unchecked -extdirs ${EXTDIR} -d ./
JFLAGS+= -target 1.6 -source 1.6


# remove command to clean up
RM = rm -vf

.PHONY:	all

all:	jtrans
	$(JC) $(JFLAGS) org/fairsim/*/*.java

linalg:
	$(JC) $(JFLAGS) org/fairsim/linalg/*.java
utils:
	$(JC) $(JFLAGS) org/fairsim/utils/*.java
fiji:
	$(JC) $(JFLAGS) org/fairsim/fiji/*.java
#tests:
#	$(JC) $(JFLAGS) org/fairsim/tests/*.java
sim_algorithm:
	$(JC) $(JFLAGS) org/fairsim/sim_algorithm/*.java
sim_gui:
	$(JC) $(JFLAGS) org/fairsim/sim_gui/*.java

jtrans:	org/fairsim/extern/jtransforms/FloatFFT_2D.class
org/fairsim/extern/jtransforms/FloatFFT_2D.class: $(wildcard org/fairsim/extern/jtransforms/*.java)
	$(JC) $(JFLAGS) org/fairsim/extern/jtransforms/*.java





# misc rules

jarsrc	: 
	git rev-parse HEAD > org/fairsim/git-version.txt ; \
	$(JAR) -cvfm fairSIM-source_$(shell head -c 10 org/fairsim/git-version.txt).jar \
	Manifest.txt plugins.config \
	org/fairsim/git-version.txt \
	org/fairsim/*/*.class  org/fairsim/extern/*/*.class  \
	org/fairsim/resources/* \
	Makefile org/fairsim/*/*.java  org/fairsim/extern/*/*.java

tarsrc	:
	git rev-parse HEAD > org/fairsim/git-version.txt ; \
	tar -cvjf fairSIM-source_$(shell head -c 10 org/fairsim/git-version.txt).tar.bz2 \
	Manifest.txt plugins.config \
	org/fairsim/git-version.txt \
	org/fairsim/resources/* \
	Makefile org/fairsim/*/*.java  org/fairsim/extern/*/*.java
    

jar:	
	git rev-parse HEAD > org/fairsim/git-version.txt ; \
	$(JAR) -cvfm fairSIM_plugin_$(shell head -c 10 org/fairsim/git-version.txt).jar \
	Manifest.txt plugins.config \
	org/fairsim/git-version.txt \
	org/fairsim/resources/* \
	org/fairsim/*/*.class  org/fairsim/extern/*/*.class 


doc:	doc/index.html

doc/index.html : $(wildcard org/fairsim/*/*.java) 
	javadoc -d doc/ -classpath ./ -extdirs ${EXTDIR} \
	-subpackages org.fairsim -exclude org.fairsim.extern.jtransforms 
#	org/fairsim/*/*.java

clean :
	$(RM) fairSIM_*.jar fairSIM_*.tar.bz2
	$(RM) org/fairsim/*/*.class
	$(RM) -r doc/*

clean-all: clean
	$(RM) org/fairsim/extern/*/*.class

