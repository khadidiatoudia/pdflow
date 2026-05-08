path = "src/PDFServer/StartServer.java"
with open(path, "r") as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if 'case "extraireTexte":' in line:
        new_lines.append('                case "extraireTexte":\n')
        new_lines.append('                    String nomET = getParam(query, "nom");\n')
        new_lines.append('                    String[] lignesText = impl.extraireTexte(nomET);\n')
        new_lines.append('                    String finalStr = String.join(" ", lignesText).replace("\"", " ");\n')
        new_lines.append('                    json = "{\\\"status\\\":\\\"success\\\", \\\"texte\\\":\\\"" + finalStr + "\\\"}";\n')
        new_lines.append('                    break;\n')
        skip = True
    elif skip and 'break;' in line:
        skip = False
    elif not skip:
        new_lines.append(line)

with open(path, "w") as f:
    f.writelines(new_lines)
