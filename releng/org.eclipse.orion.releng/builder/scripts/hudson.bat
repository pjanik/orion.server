IF NOT EXIST %WORKSPACE%\org.eclipse.releng.basebuilder cvs -Q -d :pserver:anonymous@dev.eclipse.org:/cvsroot/eclipse ex -r v20110302 -d org.eclipse.releng.basebuilder org.eclipse.releng.basebuilder

cd %WORKSPACE%

set java.home=%JAVA_HOME%
c:\java\jdk1.6.0_20\jre\bin\java -version

mkdir test
rd /S /Q %WORKSPACE%\test\eclipse

"c:\java\jdk1.6.0_20\jre\bin\java" -Xmx500m -jar %WORKSPACE%/org.eclipse.releng.basebuilder/plugins/org.eclipse.equinox.launcher.jar -application org.eclipse.equinox.p2.director -repository %repository% -i org.eclipse.orion -d %WORKSPACE%/test/eclipse

cd %WORKSPACE%\test\eclipse

start "Test Orion" orion

REM emulate a sleep for 10 seconds to let eclipse get started 
ping -n 11 127.0.0.1 >nul

for /F "tokens=2" %%I in ('TASKLIST /NH /FI "Windowtitle eq Test Orion"') do set ORION_PID=%%I

"c:\java\jdk1.6.0_20\jre\bin\java" -Xmx500m -jar %WORKSPACE%/org.eclipse.releng.basebuilder/plugins/org.eclipse.equinox.launcher.jar -Dworkspace=%WORKSPACE% -DbuildZip=%buildZip%  -Djava.home=%java.home% -application org.eclipse.ant.core.antRunner -f %WORKSPACE%/releng/org.eclipse.orion.releng/builder/scripts/runTests.xml hudsonJsTests


taskkill /PID %ORION_PID%
