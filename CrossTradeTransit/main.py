from flask import Flask, request, jsonify, render_template_string
import sqlite3
import uuid
import os

app = Flask(__name__)
# 数据库初始化
DB_PATH = "cross_trade.db"

# 初始化数据库表（首次运行自动创建）
def init_db():
    # 直接连接数据库（不管文件是否存在）
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute('''CREATE TABLE IF NOT EXISTS trades
                 (trade_id TEXT PRIMARY KEY, 
                  from_server TEXT,
                  from_player TEXT,
                  offer_item_id TEXT,
                  offer_count INTEGER,
                  offer_nbt TEXT,
                  request_item_id TEXT,
                  request_count INTEGER,
                  request_nbt TEXT,
                  to_server TEXT,
                  to_player TEXT,
                  status TEXT)''')
    conn.commit()
    conn.close()
    # 可选：打印提示，确认函数执行
    print("数据库初始化完成，trades表已创建/确认存在")

init_db()

# 可视化管理页面（方便管理员匹配交易，无需写命令）
@app.route('/')
def admin_page():
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    # 查询所有待匹配的交易
    pending_trades = c.execute('SELECT * FROM trades WHERE status="pending"').fetchall()
    # 查询已匹配/完成的交易
    done_trades = c.execute('SELECT * FROM trades WHERE status!="pending"').fetchall()
    conn.close()
    
    # 简易HTML页面（支持手动匹配）
    html = '''
    <!DOCTYPE html>
    <html>
    <head>
        <title>跨服交易管理</title>
        <meta charset="utf-8">
        <style>
            body {font-family: Arial; margin: 20px;}
            .trade-item {border: 1px solid #ccc; padding: 10px; margin: 10px 0;}
            button {background: #4CAF50; color: white; border: none; padding: 8px 16px; cursor: pointer;}
            button.cancel {background: #f44336;}
        </style>
    </head>
    <body>
        <h1>待匹配交易（Pending）</h1>
        {% for trade in pending_trades %}
        <div class="trade-item">
            <p>交易ID：{{ trade[0] }}</p>
            <p>服务器：{{ trade[1] }} | 玩家：{{ trade[2] }}</p>
            <p>提供：{{ trade[3] }} × {{ trade[4] }}</p>
            <p>请求：{{ trade[6] }} × {{ trade[7] }}</p>
            <form action="/match" method="POST">
                <input type="hidden" name="trade_id1" value="{{ trade[0] }}">
                <input type="text" name="trade_id2" placeholder="输入匹配的交易ID" required>
                <button type="submit">匹配交易</button>
            </form>
        </div>
        {% endfor %}
        
        <h1>已处理交易</h1>
        {% for trade in done_trades %}
        <div class="trade-item">
            <p>交易ID：{{ trade[0] }} | 状态：{{ trade[11] }}</p>
            <p>服务器：{{ trade[1] }} → {{ trade[9] }}</p>
            <p>物品：{{ trade[3] }} × {{ trade[4] }} ↔ {{ trade[6] }} × {{ trade[7] }}</p>
        </div>
        {% endfor %}
    </body>
    </html>
    '''
    return render_template_string(html, pending_trades=pending_trades, done_trades=done_trades)

# 接口1：接收Forge/Fabric服务器的交易请求
@app.route('/api/trade/request', methods=['POST'])
def receive_request():
    try:
        data = request.json
        # 生成唯一交易ID
        trade_id = str(uuid.uuid4())
        # 插入数据库（状态默认pending）
        conn = sqlite3.connect(DB_PATH)
        c = conn.cursor()
        c.execute('''INSERT INTO trades 
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)''',
                  (trade_id, 
                   data['from_server'], data['from_player'],
                   data['offer_item_id'], data['offer_count'], data['offer_nbt'],
                   data['request_item_id'], data['request_count'], data['request_nbt'],
                   "", "", "pending"))
        conn.commit()
        conn.close()
        return jsonify({
            "code": 200,
            "msg": "交易请求提交成功",
            "trade_id": trade_id
        })
    except Exception as e:
        return jsonify({
            "code": 500,
            "msg": f"提交失败：{str(e)}"
        })

# 接口2：管理员手动匹配两个交易
@app.route('/match', methods=['POST'])
def match_trade():
    try:
        trade_id1 = request.form['trade_id1']
        trade_id2 = request.form['trade_id2']
        
        conn = sqlite3.connect(DB_PATH)
        c = conn.cursor()
        # 查询两个交易的详情
        trade1 = c.execute('SELECT * FROM trades WHERE trade_id=?', (trade_id1,)).fetchone()
        trade2 = c.execute('SELECT * FROM trades WHERE trade_id=?', (trade_id2,)).fetchone()
        
        # 验证匹配条件：A的请求=B的提供，A的提供=B的请求，数量一致
        if not trade1 or not trade2:
            return "匹配失败：交易ID不存在", 400
        if trade1[11] != "pending" or trade2[11] != "pending":
            return "匹配失败：交易已处理", 400
        # 核心匹配逻辑（仅原版物品）
        if (trade1[6] == trade2[3] and trade1[7] == trade2[4]) and (trade2[6] == trade1[3] and trade2[7] == trade1[4]):
            # 更新交易状态为matched，并记录匹配对象
            c.execute('UPDATE trades SET to_server=?, to_player=?, status=? WHERE trade_id=?',
                      (trade2[1], trade2[2], "matched", trade_id1))
            c.execute('UPDATE trades SET to_server=?, to_player=?, status=? WHERE trade_id=?',
                      (trade1[1], trade1[2], "matched", trade_id2))
            conn.commit()
            conn.close()
            return f"匹配成功！<a href='/'>返回首页</a>"
        else:
            conn.close()
            return f"匹配失败：物品/数量不匹配<br>交易1请求：{trade1[6]}×{trade1[7]}，交易2提供：{trade2[3]}×{trade2[4]}", 400
    except Exception as e:
        return f"匹配失败：{str(e)}", 500

# 接口3：确认交易（执行物品转移）
@app.route('/api/trade/confirm', methods=['POST'])
def confirm_trade():
    try:
        trade_id = request.json['trade_id']
        conn = sqlite3.connect(DB_PATH)
        c = conn.cursor()
        trade = c.execute('SELECT * FROM trades WHERE trade_id=?', (trade_id,)).fetchone()
        
        if not trade or trade[11] != "matched":
            conn.close()
            return jsonify({
                "code": 400,
                "msg": "交易未匹配或已完成"
            })
        
        # 更新状态为confirmed
        c.execute('UPDATE trades SET status=? WHERE trade_id=?', ("confirmed", trade_id))
        conn.commit()
        conn.close()
        
        # 返回交易详情（供Forge/Fabric服务器执行物品转移）
        return jsonify({
            "code": 200,
            "trade": {
                "trade_id": trade[0],
                "from_server": trade[1],
                "from_player": trade[2],
                "offer_item_id": trade[3],
                "offer_count": trade[4],
                "offer_nbt": trade[5],
                "request_item_id": trade[6],
                "request_count": trade[7],
                "request_nbt": trade[8],
                "to_server": trade[9],
                "to_player": trade[10]
            }
        })
    except Exception as e:
        return jsonify({
            "code": 500,
            "msg": f"确认失败：{str(e)}"
        })

if __name__ == '__main__':
    # 0.0.0.0表示监听所有IP，公网可访问；端口5000
    app.run(host='0.0.0.0', port=5000, debug=True)