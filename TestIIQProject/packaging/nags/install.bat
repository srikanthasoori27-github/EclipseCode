@echo off

FOR %%F in ("%~dp0*.jar") DO java -jar "%%F"
