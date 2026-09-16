from pathlib import Path
import subprocess
import tempfile

repo = Path(__file__).resolve().parents[3]
runtime = repo / "runtime-kotlin"
classpath = list(runtime.glob("runtime-*/build/classes/kotlin/main"))
classpath += list(runtime.glob("runtime-*/build/resources/main"))
classpath += [p for p in (runtime / "runtime-cli/build/install/runtime-cli/lib").glob("*.jar")
              if not p.name.startswith("runtime-")]
if not classpath:
    raise RuntimeError("Compiled runtime classes and installed dependency jars are required")
cp = ":".join(str(p) for p in classpath)
names = ["OutboxOwnershipProbe", "CancellationProbe", "RollbackEvidenceProbe"]
with tempfile.TemporaryDirectory(prefix="skillbill-architecture-probes-") as work:
    subprocess.run(["javac", "-cp", cp, "-d", work,
                    *[str(Path(__file__).with_name(name + ".java")) for name in names]], check=True)
    for name in names:
        print(name, flush=True)
        subprocess.run(["java", "-cp", cp + ":" + work, name], check=True)
