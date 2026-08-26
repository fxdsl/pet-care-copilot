"""生成第五周可重复上传的两页文字型 PDF 样例。"""

from __future__ import annotations

from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

PROJECT_ROOT = Path(__file__).resolve().parents[1]
OUTPUT_PATH = PROJECT_ROOT / "knowledge_samples" / "幼猫喂养测试.pdf"


def add_page_number(canvas, document) -> None:  # noqa: ANN001, ARG001
    """在每页底部写入页码，便于核对回答来源。"""
    canvas.saveState()
    canvas.setFont("MicrosoftYaHei", 9)
    canvas.setFillColor(colors.HexColor("#677A72"))
    canvas.drawCentredString(A4[0] / 2, 12 * mm, f"第 {document.page} 页")
    canvas.restoreState()


def main() -> None:
    """使用系统微软雅黑生成有真实文本层的两页幼猫养护样例。"""
    pdfmetrics.registerFont(
        TTFont("MicrosoftYaHei", r"C:\Windows\Fonts\msyh.ttc", subfontIndex=0)
    )
    pdfmetrics.registerFont(
        TTFont("MicrosoftYaHeiBold", r"C:\Windows\Fonts\msyhbd.ttc", subfontIndex=0)
    )
    document = SimpleDocTemplate(
        str(OUTPUT_PATH),
        pagesize=A4,
        rightMargin=22 * mm,
        leftMargin=22 * mm,
        topMargin=22 * mm,
        bottomMargin=22 * mm,
        title="幼猫基础喂养测试样例",
        author="宠里个宠项目",
    )
    styles = getSampleStyleSheet()
    title = ParagraphStyle(
        "ChineseTitle",
        parent=styles["Title"],
        fontName="MicrosoftYaHeiBold",
        fontSize=22,
        leading=30,
        textColor=colors.HexColor("#21493D"),
        alignment=TA_CENTER,
        spaceAfter=16,
    )
    heading = ParagraphStyle(
        "ChineseHeading",
        parent=styles["Heading2"],
        fontName="MicrosoftYaHeiBold",
        fontSize=15,
        leading=22,
        textColor=colors.HexColor("#B56534"),
        spaceBefore=10,
        spaceAfter=8,
    )
    body = ParagraphStyle(
        "ChineseBody",
        parent=styles["BodyText"],
        fontName="MicrosoftYaHei",
        fontSize=11,
        leading=20,
        textColor=colors.HexColor("#30443D"),
        spaceAfter=8,
    )
    note = ParagraphStyle(
        "ChineseNote",
        parent=body,
        fontSize=9,
        leading=16,
        textColor=colors.HexColor("#6D7773"),
    )

    story = [
        Paragraph("幼猫基础喂养测试样例", title),
        Paragraph("用于第五周 PDF 提取、预览、页码来源与向量检索联调", note),
        Spacer(1, 10 * mm),
        Paragraph("一、喂养频率", heading),
        Paragraph(
            "已经断奶并开始吃幼猫粮的多数幼猫，建议把每日总量分成 3～4 小餐。"
            "四月龄以内、一次进食量较少的幼猫，可根据体重和状态安排每天 4～6 餐。",
            body,
        ),
        Paragraph("二、食物与饮水", heading),
        Paragraph(
            "选择标注为幼猫阶段的全价主食，换粮应逐步过渡。应随时提供清洁饮水，"
            "湿粮开封后按包装要求冷藏，超过安全时间的剩食不要继续喂。",
            body,
        ),
        Table(
            [["月龄", "常见安排", "观察重点"], ["2～4 月", "4～6 餐", "体重、便便、食欲"], ["4 月以上", "逐步调整为 3～4 餐", "总热量与生长速度"]],
            colWidths=[35 * mm, 58 * mm, 60 * mm],
            style=TableStyle([
                ("FONTNAME", (0, 0), (-1, -1), "MicrosoftYaHei"),
                ("FONTNAME", (0, 0), (-1, 0), "MicrosoftYaHeiBold"),
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#E5EFEA")),
                ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#B8C9C1")),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("TOPPADDING", (0, 0), (-1, -1), 7),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
            ]),
        ),
        PageBreak(),
        Paragraph("幼猫日常观察与就医边界", title),
        Paragraph("三、如何判断是否需要调整", heading),
        Paragraph(
            "每周在相近时间记录体重，并同时观察食欲、精神、呕吐、腹泻和排尿变化。"
            "具体喂食克数应根据猫粮热量、幼猫体重和包装建议计算，不应只看餐数。",
            body,
        ),
        Paragraph("四、需要尽快联系兽医的情况", heading),
        Paragraph(
            "幼猫持续拒食、反复呕吐或腹泻、精神明显沉郁、呼吸困难、抽搐或严重脱水时，"
            "应及时联系专业兽医。幼龄动物病情变化可能较快，不建议自行使用人用药。",
            body,
        ),
        Spacer(1, 12 * mm),
        Paragraph(
            "说明：本样例仅用于软件功能测试和日常养护教育，不能代替临床诊断。",
            note,
        ),
    ]
    document.build(story, onFirstPage=add_page_number, onLaterPages=add_page_number)
    print(OUTPUT_PATH)


if __name__ == "__main__":
    main()
