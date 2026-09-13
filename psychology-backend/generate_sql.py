import csv

# 读取q1.csv并生成SQL插入语句
with open('../q1.csv', 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    rows = list(reader)

# 生成INSERT语句
sql_lines = []
sql_lines.append('-- 导入心理测评题库数据 (100题)')
sql_lines.append('INSERT INTO assessment_question (id, dimension, content, option1, option2, option3, option4, option5, weight, is_reverse) VALUES')

values = []
for row in rows:
    id_val = row['题目ID']
    dimension = row['维度']
    content = row['题目内容'].replace("'", "''")  # 转义单引号
    opt1 = row['选项1']
    opt2 = row['选项2']
    opt3 = row['选项3']
    opt4 = row['选项4']
    opt5 = row['选项5']
    weight = row['计分权重']
    is_reverse = '1' if row['反向计分'] == 'True' else '0'
    
    values.append(f"('{id_val}', '{dimension}', '{content}', '{opt1}', '{opt2}', '{opt3}', '{opt4}', '{opt5}', {weight}, {is_reverse})")

sql_lines.append(',\n'.join(values) + ';')

# 写入文件
with open('sql/assessment_data.sql', 'w', encoding='utf-8') as f:
    f.write('\n'.join(sql_lines))

print(f'✓ 已生成 assessment_data.sql')
print(f'✓ 共导入 {len(rows)} 道题目')
