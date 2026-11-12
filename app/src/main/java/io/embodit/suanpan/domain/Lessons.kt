package io.embodit.suanpan.domain

// Generated from apps/suanpan-abacus/design-package.json content.lessons / whyTemplates (tools/gen_lessons.py). Do not edit by hand.

data class Txt(val zh: String, val en: String) { fun get(en: Boolean) = if (en) this.en else zh }

data class LessonDef(val n: Int, val family: String, val name: Txt, val term: Txt, val tagline: Txt, val goal: Txt, val why: Txt, val problems: List<String>)

object Lessons {
    val all: List<LessonDef> = listOf(
        LessonDef(1, "认珠子", Txt("掌柜递来一把算盘", "The shopkeeper hands you an abacus"), Txt("认珠子", "Reading beads"), Txt("靠到横梁上的珠子才算数。", "Only beads touching the bar count."), Txt("上面一颗当五，下面一颗当一，拨出题上的数。", "Top bead is five, each bottom bead is one. Set the number shown."), Txt("先会读、会拨。后面每句口诀，都是在这上面动珠子。", "Read it and set it first. Every rhyme later just moves beads from here."), listOf("=3", "=5", "=8", "=12", "=47", "=90", "=306", "=2025")),
        LessonDef(2, "直加", Txt("早上进货", "Morning delivery"), Txt("直加", "Direct add"), Txt("珠子够，直接拨上去。", "Enough beads? Just push them up."), Txt("加 3 就上 3 颗；加 7 就拨下五，再上 2 颗。", "Adding 3? Push up 3. Adding 7? Drop the five, push up 2."), Txt("这一档还有空位，放得下，别的档不用动。", "This rod still has room, so nothing else moves."), listOf("1+3", "1+8", "5+4", "10+9", "21+6", "52+7", "34+15", "211+523")),
        LessonDef(3, "直减", Txt("中午卖货", "Lunch sales"), Txt("直减", "Direct subtract"), Txt("有几颗，直接拿下来。", "Got enough? Just take them off."), Txt("减 3 就拿掉 3 颗；减 7 就去掉五，再去 2 颗。", "Taking 3? Move 3 away. Taking 7? Lift the five, then 2 more."), Txt("这档本来就够减，直接拿走就对了。", "This rod already has enough. Just take them away."), listOf("4-3", "19-9", "9-7", "27-6", "58-8", "96-51", "789-253", "1999-1424")),
        LessonDef(4, "满五加", Txt("零碎货凑个五", "Small stuff adds up to five"), Txt("满五加", "Make five"), Txt("下珠不够，拨下一颗五，多的拿掉。", "Out of bottom beads? Drop a five, take back the extra."), Txt("加 3 推不上去了？拨下一颗五，再去掉两颗。四句都这么拨。", "Can't push 3 more up? Drop a five, take two back. All four work this way."), Txt("下面只有四颗能用。凑满五，就换成上面那颗五：+3 = +5 −2。", "Only four bottom beads count. Hit five, swap for the top bead: +3 = +5 −2."), listOf("4+1", "3+2", "2+3", "1+4", "13+4", "24+3", "41+24", "234+323")),
        LessonDef(5, "破五减", Txt("找零拆开五", "Breaking a five for change"), Txt("破五减", "Break five"), Txt("下珠不够减，就动上面那颗。", "Not enough below? Use the top bead."), Txt("减 3 下面不够？先上两颗，再把五拿走。四句都这么拨。", "Not 3 down here? Push up two, then lift the five. All four work this way."), Txt("下面不够拿，就先上几颗，再把五拿走：+2 −5 = −3。", "Not enough below, so push a few up, then lift the five: +2 −5 = −3."), listOf("5-1", "6-2", "5-3", "7-4", "15-3", "56-34", "65-21", "555-123")),
        LessonDef(6, "进十加", Txt("货多装满了", "Shelf's full"), Txt("进十加", "Carry"), Txt("这档装不下，往左边进一。", "No room here? Carry one to the left."), Txt("加 7 这档装不下？先去掉 3，再往左进 1。九句都这么拨。", "No room for 7? Take 3 off here, carry one left. All nine work this way."), Txt("一档最多到九。满十就往左进一：+7 = −3 +10。", "A rod tops out at nine. Ten moves left as one bead: +7 = −3 +10."), listOf("9+2", "7+5", "8+4", "4+8", "3+9", "98+67", "89+3", "99+1")),
        LessonDef(7, "破五进十加", Txt("一大批货到", "A big shipment"), Txt("破五进十", "Carry with five"), Txt("又要进一，又得拆五。", "Carry one, break the five."), Txt("加 7 要去 3，下面又不够？先上 2，去掉五，再往左进 1。", "Need to take 3 but can't? Push up 2, lift the five, carry one left."), Txt("+7 = −3 +10；这档下面不够去 3，就上 2 去 5（+2 −5 = −3）。", "+7 = −3 +10. Can't take 3 from below, so push up 2 and lift the five (+2 −5 = −3)."), listOf("5+6", "5+7", "6+8", "5+9", "35+7", "26+8", "75+6", "965+78")),
        LessonDef(8, "退十减", Txt("跟隔壁借一", "Borrow from next door"), Txt("退十减", "Borrow"), Txt("这档不够减，跟左边借个十。", "Short here? Borrow a ten from the left."), Txt("减 7 不够？左边退 1，这档还 3。九句都这么拨。", "Can't take 7? Borrow one from the left, give 3 back here. All nine work this way."), Txt("借来一个十，花掉 7，剩下的 3 还到这档：−7 = −10 +3。", "Borrow ten, spend 7, put the 3 left over back here: −7 = −10 +3."), listOf("10-1", "12-5", "11-3", "13-4", "20-7", "30-9", "40-6", "105-28")),
        LessonDef(9, "退十还五减", Txt("借来再找零", "Borrow, then make change"), Txt("退十还五", "Borrow with five"), Txt("借了十，还的时候凑个五。", "Borrow ten, give it back with a five."), Txt("减 7 借来以后，还 3 推不上去？拨下五，再去掉 2。", "Borrowed for 7, but 3 won't fit? Drop a five, take two back."), Txt("还 3 下面放不下，就像凑五：拨下五，去掉 2（+5 −2 = +3）。", "3 won't fit below, so do the five trick: drop a five, take two back (+5 −2 = +3)."), listOf("12-6", "13-7", "14-8", "14-9", "21-6", "43-7", "54-8", "123-69")),
        LessonDef(10, "连加连减", Txt("月底对账", "Month-end books"), Txt("连加连减", "Chains"), Txt("连加带减，一口气算完。", "Add and subtract in one go."), Txt("一题三个数，上一步的结果就是下一步的起点。", "Three numbers. Each result is where the next step starts."), Txt("算盘自己记着中间数，不用写在纸上。", "The abacus holds your running total. No need to write it down."), listOf("23+48-19", "105-37+26", "76+58+39", "300-125-68", "249+386-157", "1000-1-99", "467+285-99", "888-299+111")),
    )

    /** "why" templates keyed by family name (+ leftCarry / leftBorrow); placeholders {n} {c5} {c10} {m5} {left}. */
    val why: Map<String, Txt> = mapOf(
        "直加" to Txt("这档有空位，直接拨上 {n}。", "There's room on this rod, so just add {n}."),
        "直减" to Txt("这档够减，直接拿掉 {n}。", "This rod has enough, so just take {n} away."),
        "满五加" to Txt("+{n} = +5 −{c5}。下面放不下，就拨下五，再拿掉 {c5} 颗。", "+{n} = +5 −{c5}. No room below, so drop a five and take {c5} back."),
        "破五减" to Txt("−{n} = +{c5} −5。下面不够拿，就先上 {c5} 颗，再拿走五。", "−{n} = +{c5} −5. Not enough below, so add {c5} and take the five away."),
        "进十加" to Txt("+{n} = −{c10} +10。这档先去 {c10}，再往左进一。", "+{n} = −{c10} +10. Take {c10} off here, then carry one left."),
        "破五进十加" to Txt("+{n} = −{c10} +10。这档要去 {c10}，下面不够，就先上 {m5} 再去五（+{m5} −5 = −{c10}），再往左进一。", "+{n} = −{c10} +10. Can't take {c10} from below, so push up {m5} and lift the five (+{m5} −5 = −{c10}), then carry one left."),
        "leftCarry" to Txt("左边进一也照口诀拨：{left}。", "The carry on the left follows a rhyme too: {left}."),
        "leftBorrow" to Txt("左边退一也照口诀拨：{left}。", "The borrow on the left follows a rhyme too: {left}."),
        "退十减" to Txt("−{n} = −10 +{c10}。这档不够，左边退一，这档还 {c10}。", "−{n} = −10 +{c10}. Borrow one from the left, give back {c10} here."),
        "退十还五减" to Txt("−{n} = −10 +{c10}。还 {c10} 时下面放不下，就还五去 {m5}（+5 −{m5} = +{c10}）。", "−{n} = −10 +{c10}. {c10} won't fit below, so give a five and take {m5} back (+5 −{m5} = +{c10})."),
    )

    val welcome = Txt("这是一把算盘。上面一颗当五，下面一颗当一，靠到中间横梁上的才算数。", "This is an abacus. Each top bead is five, each bottom bead is one. Only beads touching the bar count.")
}
