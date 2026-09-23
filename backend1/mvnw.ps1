#Requires -Version 5.1
<#
    Wrapper de secours pour Apache Maven sur ce poste.

    Le script officiel mvn.cmd (C:\Users\Utilisateur\Maven\apache-maven\bin\mvn.cmd)
    echoue silencieusement dans cet environnement PowerShell : il retombe sur une
    invocation "java" nue, sans classpath ni classe principale. Ce wrapper appelle
    directement le lanceur Plexus Classworlds de Maven, ce qui contourne le probleme
    sans modifier l'installation Maven existante. Verifie fonctionnel avec Maven 3.9.9
    (mvn -version) le 2026-08-17.

    Usage : depuis backend/, ex. .\mvnw.ps1 clean install
#>

$ErrorActionPreference = "Stop"

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.11"
}
$mavenHome = "C:\Users\Utilisateur\Maven\apache-maven"
$javaCmd = Join-Path $env:JAVA_HOME "bin\java.exe"
$classworldsJar = Get-ChildItem -Path (Join-Path $mavenHome "boot") -Filter "plexus-classworlds-*.jar" | Select-Object -First 1

if (-not (Test-Path $javaCmd)) {
    throw "JAVA_HOME invalide : $javaCmd introuvable."
}
if (-not $classworldsJar) {
    throw "Lanceur Plexus Classworlds introuvable sous $mavenHome\boot"
}

& $javaCmd `
    -classpath $classworldsJar.FullName `
    "-Dclassworlds.conf=$mavenHome\bin\m2.conf" `
    "-Dmaven.home=$mavenHome" `
    "-Dlibrary.jansi.path=$mavenHome\lib\jansi-native" `
    "-Dmaven.multiModuleProjectDirectory=$PWD" `
    org.codehaus.plexus.classworlds.launcher.Launcher `
    @args

exit $LASTEXITCODE
