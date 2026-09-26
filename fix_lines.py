import sys
from pathlib import Path

def break_line(file_path, line_num):
    lines = file_path.read_text().split('\n')
    line = lines[line_num - 1]
    
    if len(line) <= 120:
        return
        
    # Simple line breaking heuristics
    if ', ' in line:
        idx = line.rfind(', ')
        lines[line_num - 1] = line[:idx+1] + '\n' + ' ' * (len(line) - len(line.lstrip()) + 4) + line[idx+2:]
    elif '?' in line:
        idx = line.rfind('?')
        lines[line_num - 1] = line[:idx] + '\n' + ' ' * (len(line) - len(line.lstrip()) + 4) + line[idx:]
    elif ' =' in line:
        idx = line.find(' =')
        lines[line_num - 1] = line[:idx+2] + '\n' + ' ' * (len(line) - len(line.lstrip()) + 4) + line[idx+3:]
    elif '->' in line:
        idx = line.find('->')
        lines[line_num - 1] = line[:idx+2] + '\n' + ' ' * (len(line) - len(line.lstrip()) + 4) + line[idx+3:]
    elif ')' in line and '(' in line:
        idx = line.rfind('(')
        lines[line_num - 1] = line[:idx+1] + '\n' + ' ' * (len(line) - len(line.lstrip()) + 4) + line[idx+1:]
    else:
        # Just break it at 100
        lines[line_num - 1] = line[:100] + '\n' + ' ' * (len(line) - len(line.lstrip()) + 4) + line[100:]
        
    file_path.write_text('\n'.join(lines))

for err in sys.stdin:
    err = err.strip()
    if not err: continue
    parts = err.split(':')
    if len(parts) >= 3 and "max-line-length" in err:
        file_path = Path(parts[0])
        line_num = int(parts[1])
        try:
            break_line(file_path, line_num)
        except Exception as e:
            pass
