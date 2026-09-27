import xml.etree.ElementTree as ET

tree = ET.parse('app/src/main/res/values/strings.xml')
root = tree.getroot()

seen_names = set()
elements_to_remove = []

for elem in root:
    if elem.tag == 'string':
        name = elem.attrib.get('name')
        if name in seen_names:
            elements_to_remove.append(elem)
        else:
            seen_names.add(name)

for elem in elements_to_remove:
    root.remove(elem)

tree.write('app/src/main/res/values/strings.xml', encoding='utf-8', xml_declaration=True)
