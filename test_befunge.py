import pytest
import tomli
import subprocess
from pathlib import Path


BF_DIR = Path(__file__).parent / "bf"
RESULT_FILE = BF_DIR / "results.toml"


with RESULT_FILE.open("rb") as f:
    RESULTS = tomli.load(f)


@pytest.mark.parametrize(
    "program_file, expected",
    [(k + ".bf", v) for k, v in RESULTS.items()]
)
def test_befunge_program(program_file, expected):
    path = BF_DIR / program_file
    assert path.exists(), f"Missing Befunge file: {path}"

    output = subprocess.run(
        ["./script/befunge93", path],
        capture_output=True,
        text=True,
    ).stdout.removesuffix('\n')

    assert output == str(expected), (
        f"\nProgram: {program_file}\n"
        f"Expected:\n{expected!r}\nGot:\n{output!r}"
    )

