import sys

swagger=sys.argv[1];
template_name=sys.argv[2]
template=sys.argv[3];
escaped_template=template.replace('\n', '\\n').replace('"', '\\"')

print(str(swagger.replace('::require::' + template_name + '::', '"' + escaped_template + '"')))