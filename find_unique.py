import re

with open('app/src/main/res/values/strings.xml', 'r') as f:
    existing_content = f.read()

# Extract names from existing
existing_names = set(re.findall(r'name="([^"]+)"', existing_content))

with open('missing_strings.xml', 'r') as f:
    lines = f.readlines()

with open('clean_missing.xml', 'w') as f:
    for line in lines:
        match = re.search(r'name="([^"]+)"', line)
        if match:
            name = match.group(1)
            if name not in existing_names:
                f.write(line)
        else:
            # Maybe keep comments or other things if we want, but let's be safe and only keep string/plurals elements?
            # Actually, git diff lines might contain </plurals> or <item>.
            pass
