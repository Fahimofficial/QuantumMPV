import re

with open('strings_good.xml', 'r') as f:
    existing_content = f.read()

# Extract names from existing
existing_names = set(re.findall(r'name="([^"]+)"', existing_content))

with open('missing_strings.xml', 'r') as f:
    lines = f.readlines()

new_elements = []
skip_current_element = False

for line in lines:
    match = re.search(r'name="([^"]+)"', line)
    
    if match:
        name = match.group(1)
        if name in existing_names:
            skip_current_element = True
        else:
            skip_current_element = False
            existing_names.add(name)
            new_elements.append(line)
    else:
        # It's a line without a name, like an <item> or </plurals>
        # Only add it if we are not skipping the current element
        if not skip_current_element:
            new_elements.append(line)

# Now, insert new_elements into strings_good.xml before </resources>
existing_lines = existing_content.splitlines(True)
output_lines = []
for line in existing_lines:
    if '</resources>' in line:
        output_lines.extend(new_elements)
    output_lines.append(line)

with open('app/src/main/res/values/strings.xml', 'w') as f:
    f.writelines(output_lines)

