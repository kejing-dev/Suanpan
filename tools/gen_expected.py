import sys, importlib.util
spec = importlib.util.spec_from_file_location('kj', 'C:/Users/Admin/Downloads/spatial-app-factory/apps/suanpan-abacus/koujue_reference.py')
kj = importlib.util.module_from_spec(spec); spec.loader.exec_module(kj)
ok, rep = kj.run()
assert ok
out = []
for L in rep:
    for r in L['problems']:
        if 'koujue' not in r:
            out.append('%d|%s|%d|%d|' % (L['lesson'], r['p'], r['start'], r['answer']))
            continue
        segs = []
        for name, steps in zip(r['koujue'], r['steps']):
            # "个位 七上二去五进一（破五进十加）" -> name, family
            body = name.split(' ', 1)[1]
            fam = body[body.rindex('（') + 1:-1]
            nm = body[:body.rindex('（')]
            segs.append('%s~%s~%s' % (nm, fam, ','.join('%d:%d:%d' % tuple(s) for s in steps)))
        out.append('%d|%s|%d|%d|%s' % (L['lesson'], r['p'], r['start'], r['answer'], ';'.join(segs)))
p = 'C:/Users/Admin/Downloads/suanpan-abacus/app/src/test/resources/koujue_expected.txt'
import os; os.makedirs(os.path.dirname(p), exist_ok=True)
open(p, 'w', encoding='utf-8', newline='\n').write('\n'.join(out) + '\n')
print(len(out), 'rows')
