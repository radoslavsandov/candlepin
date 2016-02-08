
require "../client/ruby/candlepin_api"
require 'pp'
require 'optparse'
require 'benchmark'
require "logger"

CP_SERVER = "localhost"
CP_PORT = 8443
CP_ADMIN_USER = "admin"
CP_ADMIN_PASS = "admin"

PER_USER_RUN_COUNT=100


def random_string prefix=nil
  prefix ||= "rand"
  return "#{prefix}-#{rand(100000)}"
end

def user_client(username, password)
  Candlepin.new(username=username, password=password, cert=nil, key=nil, host=CP_SERVER, port=CP_PORT)
end

def consumer_client(consumer)
  Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'], CP_SERVER, CP_PORT)
end


class BaseAction
  attr_reader :title, :options

  def initialize(logger)
    @logger = logger
    @title = "NOT SET"
    @options = []
  end

  def run(client, option)
  end

end

class ListPoolsAction < BaseAction

  def initialize(logger)
    super(logger)
    @title = "List Pools"
    @options = [
      config("admin", "admin", "admin"),
      config("grumpy", "password", "snowwhite"),
      config("huey", "password", "donaldduck")
    ]
  end

  def run(client, option)
    pools = client.list_pools(:owner => option[:owner])
    @logger.info("Found pools for #{option[:username]}: #{pools.size}")
  end

  private

  def config(username, password, org)
    admin = user_client("admin", "admin")
    {:username => username, :password => password, :owner => admin.get_owner(org)["id"]}
  end

end


class ShowOwnerAction < BaseAction

  def initialize(logger)
    super(logger)
    @title = "Show Owner"
    @options = [
      config("admin", "admin", "admin"),
      config("grumpy", "password", "snowwhite"),
      config("huey", "password", "donaldduck")
    ]
  end

  def run(client, option)
    owner_key = option[:owner]
    owner = client.get_owner(owner_key)
    @logger.info("Found owner #{owner_key}")
  end

  private

  def config(username, password, owner)
    {:username => username, :password => password, :owner => owner}
  end
end


class ShowOwnerInfoAction < ShowOwnerAction

  def initialize(logger)
    super(logger)
    @title = "Show Owner Info"
  end

  def run(client, option)
    owner_key = option[:owner]
    owner = client.get_owner_info(owner_key)
    @logger.info("Found owner info for #{owner_key}")
  end

end


class ListOwnerPoolsAction < ShowOwnerAction

  def initialize(logger)
    super(logger)
    @title = "List Owner Pools"
  end

  def run(client, option)
    owner_key = option[:owner]
    pools = client.list_owner_pools(owner_key)
    @logger.info("Found #{pools.size} pools for owner #{owner_key}.")
  end
end


class ListOwnerSubscriptionsAction < ShowOwnerAction

  def initialize(logger)
    super(logger)
    @title = "List Owner Subscriptions"
    @options = [
      config("admin", "admin", "admin"),
      config("doc", "password", "snowwhite"),
      config("huey", "password", "donaldduck")
    ]
  end

  def run(client, option)
    owner_key = option[:owner]
    subs = client.list_subscriptions(owner_key)
    @logger.info("Found #{subs.size} subscriptions for owner #{owner_key}.")
  end
end


class CreateConsumerAction < BaseAction

  def initialize(logger)
    super(logger)
    @admin = user_client("admin", "admin")
    @title = "Create Consumer"
    @options = [
      config("admin", "admin", "admin"),
      config("doc", "password", "snowwhite"),
      config("huey", "password", "donaldduck")
    ]
  end

  def run(user_client, option)
    owner = option[:owner]
    consumer = create_consumer(owner)
    @logger.info("Created consumer #{consumer['uuid']} in org #{owner}")
  end

  def config(username, password, owner)
    {:username => username, :password => password, :owner => owner}
  end

  def create_consumer(owner_key, client=nil)
    client ||= @admin
    facts = {
        "distributor_version" => "sat-6.0",
        "satellite_version" => "6.0",
        "system.certificate_version" => "3.0"
    }
    client.register(random_string('dummyconsumer'), "system", nil, facts, nil, owner_key, [], [], nil, [],
        nil, [], nil, nil, nil, "8.0")
  end

end


class ShowConsumerInfoAction < CreateConsumerAction

  def initialize(logger)
    super(logger)
    @title = "List Consumer info"
  end

  def run(client, option)
    consumer = client.get_consumer(option[:consumer])
    uuid = consumer["uuid"]
    @logger.info("User #{option[:username]} found consumer #{uuid}")
  end


  def config(username, password, owner)
    cleanup(owner)

    consumer_data = create_consumer(owner)
    consumer = consumer_client(consumer_data)

    option = super(username, password, owner)
    option[:consumer] = consumer.uuid
    option[:consumer_client] = consumer
    return option
  end

  private

  # Cleanup all consumers of the org to free available
  # entitlements from the pools.
  def cleanup(owner)
    @logger.info("Unregistering consumers of org #{owner}")
    consumers = @admin.list_consumers(:owner => owner)
    consumers.each do |consumer|
      uuid = consumer["uuid"]
      @logger.info("--> Unregistering consumer #{uuid}")
      @admin.unregister(uuid)
    end
  end

end


class GetConsumerCertificateSerialsAction < ShowConsumerInfoAction

  def initialize(logger)
    super(logger)
    @title = "Get Consumer Cert Serials"
  end

  def run(user_client, option)
    consumer_cp = option[:consumer_client]
    serials = consumer_cp.list_certificate_serials
    @logger.info("Found #{serials.size} serials for consumer #{option[:consumer]}")
  end


  def config(username, password, owner)
    option = super(username, password, owner)

    pools = @admin.list_owner_pools(owner)
    if pools.size < 1
      return option
    end

    option[:consumer_client].consume_pool(pools[0]['id'])
    return option
  end

end


class GetConsumerReleaseAction < ShowConsumerInfoAction

  def initialize(logger)
    super(logger)
    @title = "List Consumer Release"
  end

  def run(user_client, option)
    consumer = option[:consumer_client]
    release = consumer.get_consumer_release()
    @logger.info("Release Version for #{consumer.uuid}: #{release['releaseVer'] ||= 'Not Set'}")
  end

end


def create_runner_thread(action, option, logger)
  return Thread.new do
    cp = user_client(option[:username], option[:password])
    for i in 1..PER_USER_RUN_COUNT
      begin
        action.run(cp, option)
      rescue => ex
        logger.info("An error of type #{ex.class} happened, message is #{ex.message}")
      end
    end
  end
end

actions = [
  #ListPoolsAction,
  #ShowOwnerAction,
  #ShowOwnerInfoAction,
  ListOwnerPoolsAction,
  #ListOwnerSubscriptionsAction,
  #CreateConsumerAction,
  #ShowConsumerInfoAction,
  #GetConsumerCertificateSerialsAction,
  #GetConsumerReleaseAction
]

#actions = [
#  ShowConsumerInfoAction
#]

def get_log_time
  Time.now.strftime("%d-%m-%Y-%H.%M")
end

log_time = get_log_time()
actions.each do |action_class|
  logger = Logger.new(File.open("#{action_class}-#{log_time}.log", File::WRONLY | File::CREAT))
  logger.info("Running action: #{action_class}")
  logger.info("Initializing action")
  action = action_class.new(logger)
  logger.info("Running #{PER_USER_RUN_COUNT} times for #{action.options.size} users.")

  all_runners = []
  action.options.each do |option|
    all_runners << create_runner_thread(action, option, logger)
  end

  Benchmark.bm(14) do |bench|
    bench.report("#{action.title}:\n") {
      all_runners.each { |runner| runner.join }
    }

  end

end
