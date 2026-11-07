from pathlib import Path
import re, sys

root = Path(sys.argv[1])
root_gradle = root / "build.gradle.kts"
app_gradle = root / "app" / "build.gradle.kts"

text = root_gradle.read_text()
if 'id("org.jetbrains.kotlin.plugin.compose")' not in text:
    text = text.replace(
        'id("org.jetbrains.kotlin.android") version "2.0.21" apply false',
        'id("org.jetbrains.kotlin.android") version "2.0.21" apply false\n    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false'
    )
root_gradle.write_text(text)

text = app_gradle.read_text()
if 'id("org.jetbrains.kotlin.plugin.compose")' not in text:
    text = text.replace(
        'id("org.jetbrains.kotlin.android")',
        'id("org.jetbrains.kotlin.android")\n    id("org.jetbrains.kotlin.plugin.compose")'
    )
text = re.sub(r'\n\s*composeOptions\s*\{\s*kotlinCompilerExtensionVersion\s*=\s*"[^"]+"\s*\}', '', text)
app_gradle.write_text(text)

assert 'org.jetbrains.kotlin.plugin.compose' in root_gradle.read_text()
assert 'org.jetbrains.kotlin.plugin.compose' in app_gradle.read_text()
assert 'kotlinCompilerExtensionVersion' not in app_gradle.read_text()
print("Compose/Kotlin 2.0 Gradle configuration patched successfully.")
