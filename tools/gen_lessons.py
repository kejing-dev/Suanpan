import json, sys, os
F = 'C:/Users/Admin/Downloads/spatial-app-factory/apps/suanpan-abacus/design-package.json'
OUT = 'C:/Users/Admin/Downloads/suanpan-abacus/app/src/main/java/io/embodit/suanpan/domain/Lessons.kt'
d = json.load(open(F, encoding='utf-8'))
c = d['content']
def q(s):
    return json.dumps(s, ensure_ascii=False)
L = []
L.append('package io.embodit.suanpan.domain')
L.append('')
L.append('// Generated from apps/suanpan-abacus/design-package.json content.lessons / whyTemplates (tools/gen_lessons.py). Do not edit by hand.')
L.append('')
L.append('data class Txt(val zh: String, val en: String) { fun get(en: Boolean) = if (en) this.en else zh }')
L.append('')
L.append('data class LessonDef(val n: Int, val family: String, val name: Txt, val term: Txt, val tagline: Txt, val goal: Txt, val why: Txt, val problems: List<String>)')
L.append('')
L.append('object Lessons {')
L.append('    val all: List<LessonDef> = listOf(')
for les in c['lessons']:
    probs = []
    for p in les['problems']:
        if les['family'] == '认珠子':
            probs.append('=%d' % p['answer'])
        else:
            probs.append(p['q'])
    def t(k):
        return 'Txt(%s, %s)' % (q(les[k]['zh']), q(les[k]['en']))
    L.append('        LessonDef(%d, %s, %s, %s, %s, %s, %s, listOf(%s)),' % (les['n'], q(les['family']), t('name'), t('term'), t('tagline'), t('goal'), t('why'), ', '.join(q(x) for x in probs)))
L.append('    )')
L.append('')
L.append('    /** "why" templates keyed by family name (+ leftCarry / leftBorrow); placeholders {n} {c5} {c10} {m5} {left}. */')
L.append('    val why: Map<String, Txt> = mapOf(')
for k, v in c['whyTemplates'].items():
    L.append('        %s to Txt(%s, %s),' % (q(k), q(v['zh']), q(v['en'])))
L.append('    )')
L.append('')
w = c['tone']['welcome']
L.append('    val welcome = Txt(%s, %s)' % (q(w['zh']), q(w['en'])))
L.append('}')
L.append('')
open(OUT, 'w', encoding='utf-8', newline='\n').write('\n'.join(L))
print('wrote', OUT)
